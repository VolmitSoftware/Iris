import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.parse
import zipfile


SPEC = importlib.util.spec_from_file_location('release', Path(__file__).with_name('release.py'))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)

SHA = 'a' * 40
OTHER_SHA = 'b' * 40


def digest(data: bytes) -> str:
    return 'sha256:' + hashlib.sha256(data).hexdigest()


class FakeGitHub:
    def __init__(self) -> None:
        self.tags: dict[str, str] = {}
        self.releases: dict[int, dict] = {}
        self.uploads: dict[int, dict[str, dict]] = {}
        self.writes: list[tuple[str, str]] = []
        self.latest: dict | None = None
        self.fail_upload: str | None = None
        self.fail_delete: int | None = None
        self.invalid_receipt: bool = False
        self.next_asset: int = 1

    def seed(self, publication: dict, assets: dict[str, bytes], draft: bool = False,
             immutable: bool = False) -> dict:
        identity: dict = {key: publication[key] for key in ('sha', 'version', 'run', 'attempt')}
        record: dict = {
            'id': len(self.releases) + 1,
            'tag_name': publication['tag'],
            'body': '<!-- release: ' + json.dumps(identity) + ' -->',
            'draft': draft,
            'prerelease': publication['channel'] == 'beta',
            'immutable': immutable,
        }
        record['upload_url'] = f"https://uploads.github.com/repos/example/product/releases/{record['id']}/assets{{?name,label}}"
        self.releases[record['id']] = record
        self.uploads[record['id']] = {}
        for name, data in assets.items():
            self.add_asset(record['id'], name, data)
        self.tags[publication['tag']] = publication['sha']
        return record

    def add_asset(self, identifier: int, name: str, data: bytes) -> dict:
        receipt: dict = {'id': self.next_asset, 'name': name, 'digest': digest(data)}
        self.next_asset += 1
        self.uploads[identifier][name] = receipt
        return copy.deepcopy(receipt)

    def tag_commit(self, tag: str) -> str | None:
        return self.tags.get(tag)

    def assets(self, identifier: int) -> list[dict]:
        return copy.deepcopy(list(self.uploads[identifier].values()))

    def request(self, method: str, path: str, payload: dict | bytes | None = None,
                missing: bool = False) -> dict | list | None:
        if method != 'GET':
            self.writes.append((method, path))
        if method == 'GET' and path.startswith('releases/tags/'):
            tag: str = path.removeprefix('releases/tags/')
            return copy.deepcopy(next((record for record in self.releases.values()
                                       if record['tag_name'] == tag), None))
        if method == 'GET' and path == 'releases/latest':
            return copy.deepcopy(self.latest)
        if method == 'POST' and path == 'releases':
            identifier: int = len(self.releases) + 1
            record: dict = copy.deepcopy(payload)
            record.update({'id': identifier, 'immutable': False,
                           'upload_url': f'https://uploads.github.com/repos/example/product/releases/{identifier}/assets{{?name,label}}'})
            self.releases[identifier] = record
            self.uploads[identifier] = {}
            return copy.deepcopy(record)
        if method == 'POST' and path.startswith('https://uploads.github.com/'):
            address: urllib.parse.SplitResult = urllib.parse.urlsplit(path)
            name: str = urllib.parse.parse_qs(address.query)['name'][0]
            if name == self.fail_upload:
                raise RuntimeError('Upload unavailable')
            identifier: int = int(address.path.split('/')[-2])
            receipt: dict = self.add_asset(identifier, name, payload)
            if self.invalid_receipt:
                receipt['digest'] = digest(b'corrupted')
            return receipt
        if method == 'POST' and path == 'git/refs':
            tag: str = payload['ref'].removeprefix('refs/tags/')
            if tag in self.tags:
                raise RuntimeError('Tag already exists')
            self.tags[tag] = payload['sha']
            return {'object': {'type': 'commit', 'sha': payload['sha']}}
        if method == 'PATCH' and path.startswith('git/refs/tags/'):
            self.tags[path.removeprefix('git/refs/tags/')] = payload['sha']
            return None
        if method == 'DELETE' and path.startswith('releases/assets/'):
            identifier: int = int(path.rsplit('/', 1)[1])
            if identifier == self.fail_delete:
                raise RuntimeError('Delete unavailable')
            for uploaded in self.uploads.values():
                for name, entry in list(uploaded.items()):
                    if entry['id'] == identifier:
                        del uploaded[name]
                        return None
            raise AssertionError(f'Unknown asset: {identifier}')
        if method == 'PATCH' and path.startswith('releases/'):
            identifier: int = int(path.rsplit('/', 1)[1])
            self.releases[identifier].update(copy.deepcopy(payload))
            if payload.get('make_latest') == 'true':
                self.latest = copy.deepcopy(self.releases[identifier])
            return copy.deepcopy(self.releases[identifier])
        raise AssertionError(f'Unexpected API request: {method} {path}')


class ReleaseTestCase(unittest.TestCase):
    def setUp(self) -> None:
        temporary: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root: Path = Path(temporary.name)
        self.config: dict = {'name': 'Example', 'platform': 'bukkit', 'versionProperty': 'exampleVersion',
                             'betaTag': 'bukkit-beta', 'volmlibRef': 'c' * 40}
        (self.root / 'gradle.properties').write_text('exampleVersion=1.2.3\n')
        self.event: dict = {'repository': {'default_branch': 'main'}, 'ref': 'refs/heads/main',
                            'after': SHA, 'head_commit': {'id': SHA, 'message': 'V+ Publish'}}
        self.environment: dict = {'GITHUB_EVENT_NAME': 'push', 'GITHUB_SHA': SHA,
                                  'GITHUB_RUN_NUMBER': '17', 'GITHUB_RUN_ATTEMPT': '2'}
        network = patch.object(release.urllib.request, 'urlopen', side_effect=AssertionError('Network is forbidden'))
        network.start()
        self.addCleanup(network.stop)

    def publication(self, channel: str = 'stable', **changes) -> dict:
        result: dict = {
            'channel': channel, 'version': '1.2.3-beta.17.2' if channel == 'beta' else '1.2.3',
            'tag': 'bukkit-beta' if channel == 'beta' else 'v1.2.3', 'sha': SHA, 'run': 17, 'attempt': 2,
            'matrix': {'include': [{'id': 'bukkit', 'loader': 'bukkit', 'minecraft': ''}]},
        }
        result.update(changes)
        return result

    def matrix(self, versions: tuple[str, ...] = ('1.20.1', '1.21.1')) -> list[dict]:
        return [{'id': f'{loader}-{version}', 'loader': loader, 'minecraft': version}
                for version in versions for loader in ('fabric', 'forge', 'neoforge')]

    def artifact_tree(self, publication: dict, extra: tuple[str, ...] = ()) -> Path:
        directory: Path = self.root / 'artifacts'
        directory.mkdir(exist_ok=True)
        descriptors: dict[str, str] = {'bukkit': 'plugin.yml', 'fabric': 'fabric.mod.json',
                                      'forge': 'META-INF/mods.toml', 'neoforge': 'META-INF/neoforge.mods.toml'}
        for target in publication['matrix']['include']:
            folder: Path = directory / f"release-{target['id']}"
            folder.mkdir()
            loader: str = target['loader']
            if loader == 'bukkit':
                filename: str = f"Example-{publication['version']}-bukkit.jar"
                metadata: str = f"name: Example\nversion: {publication['version']}\nmain: example.Plugin\n"
            else:
                display: str = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}[loader]
                filename = f"Example v{publication['version']} [{display}] {target['minecraft']}+1.0.0.jar"
                if loader == 'fabric':
                    metadata = json.dumps({'schemaVersion': 1, 'id': 'example', 'version': publication['version'],
                                           'depends': {'minecraft': '~' + target['minecraft']}})
                else:
                    metadata = (f"modLoader = 'javafml'\nloaderVersion = '[1,)'\nlicense = 'MIT'\n"
                                f"[[mods]]\nmodId = 'example'\nversion = '{publication['version']}'\n"
                                f"[[dependencies.example]]\nmodId = 'minecraft'\n"
                                f"versionRange = '[{target['minecraft']}]'\n")
            path: Path = folder / filename
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr(descriptors[loader], metadata)
                for name in extra:
                    archive.writestr(name, '')
        return directory


class PlanTests(ReleaseTestCase):
    def test_markers_are_case_sensitive(self) -> None:
        for message, channel in [('B+ Beta', 'beta'), ('V+ Release', 'stable'),
                                 ('b+ beta', 'none'), ('v+ release', 'none'), ('Fix a bug', 'none')]:
            with self.subTest(message=message):
                self.event['head_commit']['message'] = message
                result: dict = release.plan(self.root, self.config, self.event, self.environment)
                self.assertEqual(channel, result['channel'])
                if channel == 'beta':
                    self.assertEqual('1.2.3-beta.17.2', result['version'])
                    self.assertEqual('bukkit-beta', result['tag'])
                elif channel == 'stable':
                    self.assertEqual('1.2.3', result['version'])
                    self.assertEqual('v1.2.3', result['tag'])

    def test_both_markers_fail(self) -> None:
        self.event['head_commit']['message'] = 'B+ first\nV+ second'
        with self.assertRaises(ValueError):
            release.plan(self.root, self.config, self.event, self.environment)

    def test_only_pushed_default_branch_head_can_release(self) -> None:
        cases: list[tuple[dict, dict]] = [
            ({'ref': 'refs/heads/feature'}, {}), ({'ref': 'refs/tags/V+1.2.3'}, {}),
            ({'deleted': True}, {}), ({'repository': {}}, {}),
            ({'head_commit': None}, {}), ({}, {'GITHUB_EVENT_NAME': 'pull_request'}),
        ]
        for event_changes, environment_changes in cases:
            with self.subTest(event=event_changes, environment=environment_changes):
                self.assertEqual({'channel': 'none'}, release.plan(
                    self.root, self.config, {**self.event, **event_changes},
                    {**self.environment, **environment_changes}))

    def test_head_identity_must_match_event_and_environment(self) -> None:
        for field in ('after', 'commit', 'environment'):
            with self.subTest(field=field):
                event: dict = copy.deepcopy(self.event)
                environment: dict = self.environment.copy()
                if field == 'commit':
                    event['head_commit']['id'] = OTHER_SHA
                elif field == 'environment':
                    environment['GITHUB_SHA'] = 'not-a-sha'
                else:
                    event['after'] = OTHER_SHA
                with self.assertRaises(ValueError):
                    release.plan(self.root, self.config, event, environment)

    def test_old_commits_do_not_trigger_release(self) -> None:
        self.event['commits'] = [{'id': OTHER_SHA, 'message': 'V+ Release'}]
        self.event['head_commit']['message'] = 'Fix a bug'
        self.assertEqual({'channel': 'none'}, release.plan(self.root, self.config, self.event, self.environment))

    def test_invalid_or_duplicate_versions_fail(self) -> None:
        for value in ('', 'exampleVersion=01.2.3\n', 'exampleVersion=1.2\n',
                      'exampleVersion=1.2.3-beta.1\n', 'exampleVersion=1.2.3\nexampleVersion=2.3.4\n'):
            with self.subTest(value=value):
                (self.root / 'gradle.properties').write_text(value)
                with self.assertRaises(ValueError):
                    release.plan(self.root, self.config, self.event, self.environment)

    def test_invalid_run_identity_fails(self) -> None:
        for key in ('GITHUB_RUN_NUMBER', 'GITHUB_RUN_ATTEMPT'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.plan(self.root, self.config, self.event, {**self.environment, key: '0'})

    def test_minecraft_file_matrix_contains_all_loaders(self) -> None:
        (self.root / 'targets.json').write_text(json.dumps({'1.20.1': {}, '1.21.1': {}}))
        config: dict = {**self.config, 'platform': 'modded', 'targets': 'targets.json'}
        self.assertEqual(self.matrix(), release.targets(self.root, config))

    def test_minecraft_directory_matrix_matches_files(self) -> None:
        folder: Path = self.root / 'targets'
        folder.mkdir()
        (folder / '1.20.1.json').write_text(json.dumps({'minecraft': '1.20.1',
                                                      'loaders': ['fabric', 'forge', 'neoforge']}))
        config: dict = {**self.config, 'platform': 'modded', 'targets': 'targets'}
        self.assertEqual(self.matrix(('1.20.1',)), release.targets(self.root, config))
        for minecraft, loaders in [('1.20.2', ['fabric', 'forge', 'neoforge']), ('1.20.1', ['fabric'])]:
            with self.subTest(minecraft=minecraft, loaders=loaders):
                (folder / '1.20.1.json').write_text(json.dumps({'minecraft': minecraft, 'loaders': loaders}))
                with self.assertRaises(ValueError):
                    release.targets(self.root, config)

    def test_empty_or_invalid_minecraft_matrix_fails(self) -> None:
        for content in ({}, {'invalid': {}}):
            with self.subTest(content=content):
                (self.root / 'targets.json').write_text(json.dumps(content))
                with self.assertRaises(ValueError):
                    release.targets(self.root, {**self.config, 'platform': 'modded', 'targets': 'targets.json'})


class ArtifactTests(ReleaseTestCase):
    def test_complete_modded_matrix_and_checksums(self) -> None:
        publication: dict = self.publication(matrix={'include': self.matrix()})
        assets: dict[str, bytes] = release.collect_artifacts(self.artifact_tree(publication), publication)
        self.assertEqual(7, len(assets))
        checksums: bytes = assets.pop('SHA256SUMS-1.2.3.txt')
        self.assertEqual(''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n'
                                 for name, data in sorted(assets.items())).encode(), checksums)

    def test_missing_or_extra_matrix_target_fails(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        (directory / 'release-extra').mkdir()
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)
        (directory / 'release-extra').rmdir()
        (directory / 'release-bukkit').rename(directory / 'release-wrong')
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_intermediate_artifact_beside_runtime_fails(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        (directory / 'release-bukkit' / 'sources.jar').write_bytes(b'sources')
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_symbolic_link_artifacts_fail(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        path: Path = next((directory / 'release-bukkit').iterdir())
        target: Path = self.root / path.name
        path.rename(target)
        path.symlink_to(target)
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_bukkit_is_forbidden_in_modded_release(self) -> None:
        publication: dict = self.publication(matrix={'include': self.matrix(('1.20.1',))})
        with self.assertRaises(ValueError):
            release.collect_artifacts(self.artifact_tree(publication, ('plugin.yml',)), publication)

    def test_velocity_is_forbidden_in_bukkit_release(self) -> None:
        publication: dict = self.publication()
        with self.assertRaises(ValueError):
            release.collect_artifacts(self.artifact_tree(publication, ('velocity-plugin.json',)), publication)

    def test_modded_descriptor_is_forbidden_in_bukkit_release(self) -> None:
        publication: dict = self.publication()
        with self.assertRaises(ValueError):
            release.collect_artifacts(self.artifact_tree(publication, ('fabric.mod.json',)), publication)

    def test_required_platform_descriptor_is_checked(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        path: Path = next((directory / 'release-bukkit').iterdir())
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('fabric.mod.json', '{}')
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_wrong_version_and_embedded_version_suffix_fail(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        path: Path = next((directory / 'release-bukkit').iterdir())
        for version in ('9.8.7', '11.2.3'):
            with self.subTest(version=version):
                changed: Path = path.with_name(f'Example-{version}-bukkit.jar')
                path.rename(changed)
                with self.assertRaises(ValueError):
                    release.collect_artifacts(directory, publication)
                changed.rename(path)

    def test_descriptor_version_must_match_release(self) -> None:
        publication: dict = self.publication()
        directory: Path = self.artifact_tree(publication)
        path: Path = next((directory / 'release-bukkit').iterdir())
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('plugin.yml', 'name: Example\nversion: 1.2.4\nmain: example.Plugin\n')
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_modded_descriptor_versions_must_match_release(self) -> None:
        publication: dict = self.publication(matrix={'include': self.matrix(('1.20.1',))})
        directory: Path = self.artifact_tree(publication)
        for folder in directory.iterdir():
            with self.subTest(target=folder.name):
                path: Path = next(folder.iterdir())
                original: bytes = path.read_bytes()
                with zipfile.ZipFile(path) as archive:
                    descriptor: str = archive.namelist()[0]
                    contents: str = archive.read(descriptor).decode().replace('1.2.3', '1.2.4')
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr(descriptor, contents)
                with self.assertRaises(ValueError):
                    release.collect_artifacts(directory, publication)
                path.write_bytes(original)

    def test_modded_filename_must_match_minecraft_target(self) -> None:
        publication: dict = self.publication(matrix={'include': self.matrix(('1.20.1',))})
        directory: Path = self.artifact_tree(publication)
        path: Path = next((directory / 'release-fabric-1.20.1').iterdir())
        path.rename(path.with_name(path.name.replace('1.20.1', '1.21.1')))
        with self.assertRaises(ValueError):
            release.collect_artifacts(directory, publication)

    def test_modded_metadata_must_match_minecraft_target(self) -> None:
        publication: dict = self.publication(matrix={'include': self.matrix(('1.20.1',))})
        directory: Path = self.artifact_tree(publication)
        for folder in directory.iterdir():
            with self.subTest(target=folder.name):
                path: Path = next(folder.iterdir())
                original: bytes = path.read_bytes()
                with zipfile.ZipFile(path) as archive:
                    descriptor: str = archive.namelist()[0]
                    contents: str = archive.read(descriptor).decode().replace('1.20.1', '1.21.1')
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr(descriptor, contents)
                with self.assertRaises(ValueError):
                    release.collect_artifacts(directory, publication)
                path.write_bytes(original)


class PublicationTests(ReleaseTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.github: FakeGitHub = FakeGitHub()
        self.assets: dict[str, bytes] = {'Example-1.2.3-bukkit.jar': b'jar', 'SHA256SUMS-1.2.3.txt': b'checksums'}

    def test_new_stable_release_is_published_at_head(self) -> None:
        release.publish(self.github, self.config, self.publication(), self.assets)
        record: dict = self.github.releases[1]
        self.assertEqual(SHA, self.github.tags['v1.2.3'])
        self.assertFalse(record['draft'])
        self.assertFalse(record['prerelease'])
        self.assertEqual('true', record['make_latest'])
        self.assertEqual(set(self.assets), set(self.github.uploads[1]))

    def test_stable_existing_tag_collision_has_no_writes(self) -> None:
        self.github.tags['v1.2.3'] = OTHER_SHA
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual([], self.github.writes)
        self.assertEqual(OTHER_SHA, self.github.tags['v1.2.3'])

    def test_stable_same_content_rerun_has_no_writes(self) -> None:
        self.github.seed(self.publication(), self.assets, immutable=True)
        result: str = release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertIn('already published', result)
        self.assertEqual([], self.github.writes)

    def test_published_stable_artifacts_cannot_be_replaced(self) -> None:
        self.github.seed(self.publication(), self.assets, immutable=True)
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(),
                            {**self.assets, 'Example-1.2.3-bukkit.jar': b'different'})
        self.assertEqual([], self.github.writes)

    def test_stable_release_identity_collision_has_no_writes(self) -> None:
        self.github.seed(self.publication(sha=OTHER_SHA), self.assets)
        self.github.tags['v1.2.3'] = SHA
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual([], self.github.writes)

    def test_stable_release_cannot_reuse_published_prerelease(self) -> None:
        record: dict = self.github.seed(self.publication(), self.assets)
        record['prerelease'] = True
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual([], self.github.writes)

    def test_partially_uploaded_stable_draft_resumes(self) -> None:
        record: dict = self.github.seed(self.publication(), {'Example-1.2.3-bukkit.jar': b'jar'}, draft=True)
        original_id: int = self.github.uploads[record['id']]['Example-1.2.3-bukkit.jar']['id']
        release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertFalse(self.github.releases[record['id']]['draft'])
        self.assertEqual(original_id, self.github.uploads[record['id']]['Example-1.2.3-bukkit.jar']['id'])
        self.assertEqual(set(self.assets), set(self.github.uploads[record['id']]))
        self.assertEqual(1, len(self.github.releases))

    def test_conflicting_draft_asset_fails_without_replacement(self) -> None:
        self.github.seed(self.publication(), {'Example-1.2.3-bukkit.jar': b'other'}, draft=True)
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual([], self.github.writes)
        self.assertTrue(self.github.releases[1]['draft'])

    def test_newer_beta_run_or_attempt_is_preserved(self) -> None:
        for run, attempt in ((18, 1), (17, 3)):
            with self.subTest(run=run, attempt=attempt):
                github: FakeGitHub = FakeGitHub()
                github.seed(self.publication('beta', run=run, attempt=attempt, sha=OTHER_SHA), self.assets)
                result: str = release.publish(github, self.config, self.publication('beta'), self.assets)
                self.assertIn('Skipped', result)
                self.assertEqual([], github.writes)
                self.assertEqual(OTHER_SHA, github.tags['bukkit-beta'])

    def test_successful_beta_replaces_assets_and_remains_prerelease(self) -> None:
        previous: dict = self.publication('beta', run=16, attempt=1, sha=OTHER_SHA, version='1.2.3-beta.16.1')
        record: dict = self.github.seed(previous, {'old.jar': b'old'})
        release.publish(self.github, self.config, self.publication('beta'), self.assets)
        current: dict = self.github.releases[record['id']]
        self.assertEqual(SHA, self.github.tags['bukkit-beta'])
        self.assertTrue(current['prerelease'])
        self.assertFalse(current['draft'])
        self.assertEqual('false', current['make_latest'])
        self.assertEqual(set(self.assets), set(self.github.uploads[record['id']]))

    def test_failed_beta_upload_preserves_tag_and_assets_in_recoverable_draft(self) -> None:
        previous: dict = self.publication('beta', run=16, attempt=1, sha=OTHER_SHA, version='1.2.3-beta.16.1')
        record: dict = self.github.seed(previous, {'old.jar': b'old'})
        self.github.fail_upload = 'SHA256SUMS-1.2.3.txt'
        with self.assertRaises(RuntimeError):
            release.publish(self.github, self.config, self.publication('beta'), self.assets)
        self.assertEqual(OTHER_SHA, self.github.tags['bukkit-beta'])
        self.assertTrue(self.github.releases[record['id']]['draft'])
        self.assertEqual(17, release.release_metadata(self.github.releases[record['id']])['run'])
        self.assertIn('old.jar', self.github.uploads[record['id']])
        self.assertFalse(any(method == 'DELETE' or path.startswith('git/refs')
                             for method, path in self.github.writes))

    def test_failed_beta_cleanup_resumes_without_reuploading_verified_assets(self) -> None:
        previous: dict = self.publication('beta', run=16, attempt=1, sha=OTHER_SHA, version='1.2.3-beta.16.1')
        record: dict = self.github.seed(previous, {'old.jar': b'old'})
        self.github.fail_delete = self.github.uploads[record['id']]['old.jar']['id']
        with self.assertRaises(RuntimeError):
            release.publish(self.github, self.config, self.publication('beta'), self.assets)
        self.assertTrue(self.github.releases[record['id']]['draft'])
        self.assertEqual(OTHER_SHA, self.github.tags['bukkit-beta'])
        uploaded_ids: dict[str, int] = {name: self.github.uploads[record['id']][name]['id'] for name in self.assets}
        self.github.fail_delete = None
        release.publish(self.github, self.config, self.publication('beta'), self.assets)
        self.assertFalse(self.github.releases[record['id']]['draft'])
        self.assertEqual(SHA, self.github.tags['bukkit-beta'])
        self.assertEqual(uploaded_ids, {name: entry['id'] for name, entry in self.github.uploads[record['id']].items()})

    def test_incomplete_newer_beta_draft_blocks_older_run(self) -> None:
        self.github.seed(self.publication('beta', run=18, sha=OTHER_SHA), {}, draft=True)
        result: str = release.publish(self.github, self.config, self.publication('beta'), self.assets)
        self.assertIn('Skipped', result)
        self.assertEqual([], self.github.writes)
        self.assertTrue(self.github.releases[1]['draft'])

    def test_starter_upload_in_draft_is_replaced_before_publication(self) -> None:
        self.github.seed(self.publication(), {'Example-1.2.3-bukkit.jar': b''}, draft=True)
        starter: dict = self.github.uploads[1]['Example-1.2.3-bukkit.jar']
        identifier: int = starter['id']
        starter.update({'state': 'starter', 'digest': None})
        release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertFalse(self.github.releases[1]['draft'])
        self.assertNotEqual(identifier, self.github.uploads[1]['Example-1.2.3-bukkit.jar']['id'])
        self.assertEqual(digest(b'jar'), self.github.uploads[1]['Example-1.2.3-bukkit.jar']['digest'])

    def test_invalid_upload_digest_does_not_create_tag_or_publish(self) -> None:
        self.github.invalid_receipt = True
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual({}, self.github.tags)
        self.assertTrue(self.github.releases[1]['draft'])

    def test_beta_will_not_replace_stable_or_immutable_release(self) -> None:
        for immutable, prerelease in ((True, True), (False, False)):
            with self.subTest(immutable=immutable, prerelease=prerelease):
                github: FakeGitHub = FakeGitHub()
                record: dict = github.seed(self.publication('beta', run=16, sha=OTHER_SHA), self.assets,
                                           immutable=immutable)
                record['prerelease'] = prerelease
                with self.assertRaises(ValueError):
                    release.publish(github, self.config, self.publication('beta'), self.assets)
                self.assertEqual([], github.writes)

    def test_unowned_existing_release_is_preserved(self) -> None:
        record: dict = self.github.seed(self.publication(), self.assets)
        record['body'] = 'Published manually'
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual([], self.github.writes)

    def test_beta_tag_without_release_is_preserved(self) -> None:
        self.github.tags['bukkit-beta'] = OTHER_SHA
        with self.assertRaises(ValueError):
            release.publish(self.github, self.config, self.publication('beta'), self.assets)
        self.assertEqual([], self.github.writes)

    def test_modded_stable_release_never_changes_latest(self) -> None:
        self.github.latest = {'tag_name': 'v1.0.0'}
        release.publish(self.github, {**self.config, 'platform': 'modded'}, self.publication(), self.assets)
        self.assertFalse(self.github.releases[1]['prerelease'])
        self.assertEqual('false', self.github.releases[1]['make_latest'])
        self.assertEqual('v1.0.0', self.github.latest['tag_name'])

    def test_bukkit_backport_does_not_replace_newer_latest(self) -> None:
        for latest in ('v1.3.0', 'v2.0.0', 'v1.2.3'):
            with self.subTest(latest=latest):
                github: FakeGitHub = FakeGitHub()
                github.latest = {'tag_name': latest}
                release.publish(github, self.config, self.publication(), self.assets)
                self.assertEqual('false', github.releases[1]['make_latest'])
                self.assertEqual(latest, github.latest['tag_name'])

    def test_newer_bukkit_release_becomes_latest(self) -> None:
        self.github.latest = {'tag_name': 'v1.2.2'}
        release.publish(self.github, self.config, self.publication(), self.assets)
        self.assertEqual('true', self.github.releases[1]['make_latest'])
        self.assertEqual('v1.2.3', self.github.latest['tag_name'])


class GitHubReadTests(ReleaseTestCase):
    def test_annotated_tag_resolves_to_commit(self) -> None:
        github: release.GitHub = release.GitHub('example/product', 'test-token')
        responses: dict[str, dict] = {
            'git/ref/tags/v1.2.3': {'object': {'type': 'tag', 'sha': OTHER_SHA}},
            f'git/tags/{OTHER_SHA}': {'object': {'type': 'commit', 'sha': SHA}},
        }
        with patch.object(github, 'request', side_effect=lambda method, path, **options: responses[path]):
            self.assertEqual(SHA, github.tag_commit('v1.2.3'))

    def test_noncommit_tag_cannot_be_published(self) -> None:
        github: release.GitHub = release.GitHub('example/product', 'test-token')
        with patch.object(github, 'request', return_value={'object': {'type': 'tree', 'sha': SHA}}):
            with self.assertRaises(ValueError):
                github.tag_commit('v1.2.3')

    def test_assets_include_all_pages(self) -> None:
        github: release.GitHub = release.GitHub('example/product', 'test-token')
        first: list[dict] = [{'id': number, 'name': f'{number}.jar'} for number in range(100)]
        last: list[dict] = [{'id': 100, 'name': 'last.jar'}]
        with patch.object(github, 'request', side_effect=[first, last]):
            self.assertEqual(first + last, github.assets(1))

    def test_invalid_repository_is_rejected(self) -> None:
        for repository in ('example', '../example/product', 'example/product/extra'):
            with self.subTest(repository=repository), self.assertRaises(ValueError):
                release.GitHub(repository, 'test-token')


class DependencyResolutionTests(ReleaseTestCase):
    def test_branch_resolves_to_full_commit_sha(self) -> None:
        with patch.object(release.GitHub, 'request', return_value={'sha': SHA}) as request:
            self.assertEqual(SHA, release.resolve_dependency('example/product', 'master', 'test-token'))
        request.assert_called_once_with('GET', 'commits/master')

    def test_full_commit_reference_is_verified(self) -> None:
        with patch.object(release.GitHub, 'request', return_value={'sha': SHA}) as request:
            self.assertEqual(SHA, release.resolve_dependency('example/product', SHA, 'test-token'))
        request.assert_called_once_with('GET', f'commits/{SHA}')

    def test_incomplete_or_invalid_resolved_sha_fails(self) -> None:
        for sha in ('', 'a' * 39, 'a' * 41, 'A' * 40, 'g' * 40, 'master'):
            with self.subTest(sha=sha), patch.object(release.GitHub, 'request', return_value={'sha': sha}):
                with self.assertRaises(ValueError):
                    release.resolve_dependency('example/product', 'master', 'test-token')

    def test_reference_is_encoded_as_single_url_path_component(self) -> None:
        with patch.object(release.GitHub, 'request', return_value={'sha': SHA}) as request:
            self.assertEqual(SHA, release.resolve_dependency('example/product', 'feature/next+release #?', 'test-token'))
        request.assert_called_once_with('GET', 'commits/feature%2Fnext%2Brelease%20%23%3F')


class MainTests(ReleaseTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.config.update({'volmlibRef': 'master', 'wormholesRef': 'release/api'})
        (self.root / '.github').mkdir()
        (self.root / '.github/release.json').write_text(json.dumps(self.config))
        self.event['head_commit']['message'] = 'B+ Publish beta'
        (self.root / 'event.json').write_text(json.dumps(self.event))
        self.environment.update({
            'GITHUB_EVENT_PATH': str(self.root / 'event.json'),
            'GITHUB_OUTPUT': str(self.root / 'output.txt'),
            'GITHUB_REPOSITORY': 'example/product',
            'GH_TOKEN': 'test-token',
        })
        script = patch.object(release, '__file__', str(self.root / '.github/release.py'))
        script.start()
        self.addCleanup(script.stop)
        output = patch('sys.stdout', new=io.StringIO())
        output.start()
        self.addCleanup(output.stop)

    def test_plan_freezes_each_dependency_branch_once(self) -> None:
        with patch.dict(release.os.environ, self.environment, clear=True), \
                patch.object(release.sys, 'argv', ['release.py', 'plan']), \
                patch.object(release.GitHub, 'request', side_effect=[{'sha': SHA}, {'sha': OTHER_SHA}]) as request:
            release.main()
        outputs: dict[str, str] = dict(line.split('=', 1) for line in (self.root / 'output.txt').read_text().splitlines())
        self.assertEqual(SHA, outputs['volmlib_ref'])
        self.assertEqual(OTHER_SHA, outputs['wormholes_ref'])
        self.assertEqual('1.2.3-beta.17.2', outputs['version'])
        self.assertEqual('2', outputs['attempt'])
        self.assertEqual(2, request.call_count)
        self.assertEqual(['commits/master', 'commits/release%2Fapi'], [call.args[1] for call in request.call_args_list])

    def test_unmarked_push_never_resolves_dependencies(self) -> None:
        self.event['head_commit']['message'] = 'Fix a bug'
        (self.root / 'event.json').write_text(json.dumps(self.event))
        with patch.dict(release.os.environ, self.environment, clear=True), \
                patch.object(release.sys, 'argv', ['release.py', 'plan']), \
                patch.object(release, 'resolve_dependency', side_effect=AssertionError('Unexpected dependency lookup')):
            release.main()
        self.assertEqual('channel=none\n', (self.root / 'output.txt').read_text())

    def test_publish_rerun_uses_original_planned_attempt_and_dependency_commits(self) -> None:
        publication: dict = self.publication('beta')
        artifacts: Path = self.artifact_tree(publication)
        github: FakeGitHub = FakeGitHub()
        self.environment.update({'GITHUB_RUN_ATTEMPT': '3', 'RELEASE_ATTEMPT': '2',
                                  'RELEASE_VOLMLIB_REF': SHA, 'RELEASE_WORMHOLES_REF': OTHER_SHA})
        with patch.dict(release.os.environ, self.environment, clear=True), \
                patch.object(release.sys, 'argv', ['release.py', 'publish', '--artifacts', str(artifacts)]), \
                patch.object(release, 'GitHub', return_value=github), \
                patch.object(release, 'resolve_dependency', side_effect=AssertionError('Dependencies must stay frozen')):
            release.main()
            metadata: dict = release.release_metadata(github.releases[1])
            self.assertEqual('1.2.3-beta.17.2', metadata['version'])
            self.assertEqual(2, metadata['attempt'])
            self.assertEqual(SHA, metadata['volmlib_ref'])
            self.assertEqual(OTHER_SHA, metadata['wormholes_ref'])
            self.assertFalse(github.releases[1]['draft'])
            self.assertEqual({'Example-1.2.3-beta.17.2-bukkit.jar', 'SHA256SUMS-1.2.3-beta.17.2.txt'},
                             set(github.uploads[1]))
            writes: list[tuple[str, str]] = github.writes.copy()
            release.os.environ['GITHUB_RUN_ATTEMPT'] = '4'
            release.main()
            self.assertEqual(writes, github.writes)

    def test_publish_rejects_unresolved_dependency_reference_before_writing(self) -> None:
        artifacts: Path = self.artifact_tree(self.publication('beta'))
        github: FakeGitHub = FakeGitHub()
        self.environment.update({'RELEASE_ATTEMPT': '2', 'RELEASE_VOLMLIB_REF': SHA,
                                  'RELEASE_WORMHOLES_REF': OTHER_SHA})
        for key in ('RELEASE_VOLMLIB_REF', 'RELEASE_WORMHOLES_REF'):
            with self.subTest(key=key), \
                    patch.dict(release.os.environ, {**self.environment, key: 'master'}, clear=True), \
                    patch.object(release.sys, 'argv', ['release.py', 'publish', '--artifacts', str(artifacts)]), \
                    patch.object(release, 'GitHub', return_value=github):
                with self.assertRaises(ValueError):
                    release.main()
                self.assertEqual([], github.writes)


if __name__ == '__main__':
    unittest.main()
