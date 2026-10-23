import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import zipfile


def read_config(root: Path) -> dict:
    return json.loads((root / '.github/release.json').read_text())


def targets(root: Path, config: dict) -> list[dict[str, str]]:
    if config['platform'] == 'bukkit':
        return [{'id': 'bukkit', 'loader': 'bukkit', 'minecraft': ''}]
    manifest: Path = root / config['targets']
    if manifest.is_file():
        versions: dict = json.loads(manifest.read_text())
        entries: list[tuple[str, list[str]]] = [(version, ['fabric', 'forge', 'neoforge']) for version in versions]
    else:
        entries = []
        for path in sorted(manifest.glob('*.json')):
            target: dict = json.loads(path.read_text())
            if path.stem != target['minecraft']:
                raise ValueError(f'Minecraft manifest does not match {path.name}')
            entries.append((target['minecraft'], list(target['loaders'])))
    matrix: list[dict[str, str]] = []
    for version, loaders in entries:
        if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', version):
            raise ValueError(f'Invalid Minecraft version: {version}')
        if set(loaders) != {'fabric', 'forge', 'neoforge'}:
            raise ValueError(f'Incomplete loader matrix for {version}')
        for loader in sorted(loaders):
            matrix.append({'id': f'{loader}-{version}', 'loader': loader, 'minecraft': version})
    if not matrix:
        raise ValueError('Release matrix is empty')
    return matrix


def plan(root: Path, config: dict, event: dict, environment: dict) -> dict:
    branch: str = event.get('repository', {}).get('default_branch', '')
    if (environment.get('GITHUB_EVENT_NAME') != 'push' or event.get('deleted')
            or not branch or event.get('ref') != f'refs/heads/{branch}'):
        return {'channel': 'none'}
    commit: dict = event.get('head_commit') or {}
    message: str = commit.get('message', '')
    beta: bool = 'B+' in message
    stable: bool = 'V+' in message
    if beta and stable:
        raise ValueError('Use exactly one release marker: B+ or V+')
    if not beta and not stable:
        return {'channel': 'none'}
    sha: str = environment['GITHUB_SHA']
    if event.get('after') != sha or commit.get('id') != sha or not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise ValueError('Release commit does not match the pushed HEAD')
    key: str = config['versionProperty']
    properties: str = (root / 'gradle.properties').read_text()
    values: list[str] = re.findall(rf'^{re.escape(key)}\s*=\s*(\S+)\s*$', properties, re.MULTILINE)
    if len(values) != 1 or not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', values[0]):
        raise ValueError(f'{key} must declare one stable major.minor.patch version')
    run: int = int(environment['GITHUB_RUN_NUMBER'])
    attempt: int = int(environment['GITHUB_RUN_ATTEMPT'])
    if run < 1 or attempt < 1:
        raise ValueError('Invalid workflow run identity')
    version: str = f'{values[0]}-beta.{run}.{attempt}' if beta else values[0]
    return {
        'channel': 'beta' if beta else 'stable',
        'version': version,
        'version_property': key,
        'tag': config['betaTag'] if beta else f'v{version}',
        'sha': sha,
        'run': run,
        'attempt': attempt,
        'matrix': {'include': targets(root, config)},
        'volmlib_ref': config['volmlibRef'],
        'wormholes_ref': config.get('wormholesRef', ''),
    }


def collect_artifacts(directory: Path, release: dict) -> dict[str, bytes]:
    expected: dict[str, dict] = {f"release-{entry['id']}": entry for entry in release['matrix']['include']}
    if {path.name for path in directory.iterdir()} != set(expected):
        raise ValueError('Downloaded artifacts do not match the complete release matrix')
    assets: dict[str, bytes] = {}
    descriptors: dict[str, str] = {'bukkit': 'plugin.yml', 'fabric': 'fabric.mod.json',
                                  'forge': 'META-INF/mods.toml', 'neoforge': 'META-INF/neoforge.mods.toml'}
    for name, target in expected.items():
        folder: Path = directory / name
        paths: list[Path] = list(folder.iterdir()) if folder.is_dir() and not folder.is_symlink() else []
        if len(paths) != 1 or paths[0].suffix != '.jar' or paths[0].is_symlink() or not paths[0].is_file():
            raise ValueError(f'{name} must contain exactly one runtime jar')
        path: Path = paths[0]
        loader: str = target['loader']
        suffix: str = re.escape(release['version']) + r'-bukkit\.jar'
        if loader != 'bukkit':
            display: str = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}[loader]
            suffix = (r'v' + re.escape(release['version']) + r' \[' + display + r'\] '
                      + re.escape(target['minecraft']) + r'\+[^/\s]+\.jar')
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*' + ('-' if loader == 'bukkit' else ' ') + suffix, path.name):
            raise ValueError(f'Artifact does not match the release target: {path.name}')
        with zipfile.ZipFile(path) as archive:
            entries: set[str] = set(archive.namelist())
            descriptor: str = descriptors[target['loader']]
            if descriptor not in entries:
                raise ValueError(f'{path.name} is missing {descriptor}')
            excluded: set[str] = set(descriptors.values()) - {descriptor}
            if 'velocity-plugin.json' in entries or entries.intersection(excluded):
                raise ValueError(f'{path.name} contains an excluded platform')
            contents: str = archive.read(descriptor).decode()
            if loader == 'bukkit':
                match: re.Match | None = re.search(r'^version:\s*[\'\"]?([^\s\'\"]+)', contents, re.MULTILINE)
                version: str | None = match[1] if match else None
            elif loader == 'fabric':
                metadata: dict = json.loads(contents)
                version = metadata.get('version')
                if metadata.get('depends', {}).get('minecraft') not in (target['minecraft'], '~' + target['minecraft']):
                    raise ValueError(f'{path.name} declares a different Minecraft target')
            else:
                metadata = tomllib.loads(contents)
                mods: list[dict] = metadata.get('mods', [])
                version = mods[0].get('version') if len(mods) == 1 else None
                dependencies: list[dict] = metadata.get('dependencies', {}).get(mods[0].get('modId'), []) if len(mods) == 1 else []
                minecraft: list[str] = [entry.get('versionRange') for entry in dependencies if entry.get('modId') == 'minecraft']
                if minecraft != ['[' + target['minecraft'] + ']']:
                    raise ValueError(f'{path.name} declares a different Minecraft target')
            if version != release['version']:
                raise ValueError(f'{path.name} declares a different product version')
        asset_name: str = re.sub(r'[^A-Za-z0-9._-]+', '-', path.name)
        if asset_name in assets:
            raise ValueError(f'Duplicate release asset: {asset_name}')
        assets[asset_name] = path.read_bytes()
    checksums: str = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(assets.items()))
    assets[f"SHA256SUMS-{release['version']}.txt"] = checksums.encode()
    return assets


class GitHub:
    def __init__(self, repository: str, token: str):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
            raise ValueError('Invalid GitHub repository')
        self.repository = repository
        self.token = token

    def request(self, method: str, path: str, payload: dict | bytes | None = None, missing: bool = False) -> dict | list | None:
        url: str = path if path.startswith('https://uploads.github.com/') else f'https://api.github.com/repos/{self.repository}/{path}'
        binary: bool = isinstance(payload, bytes)
        data: bytes | None = payload if binary else json.dumps(payload).encode() if payload is not None else None
        request: urllib.request.Request = urllib.request.Request(url, data=data, method=method, headers={
            'Authorization': f'Bearer {self.token}',
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2026-03-10',
            'Content-Type': 'application/octet-stream' if binary else 'application/json',
            'User-Agent': 'Volmit-release',
        })
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                body: bytes = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            raise RuntimeError(f'GitHub {method} {urllib.parse.urlsplit(url).path}: HTTP {error.code}') from error

    def tag_commit(self, tag: str) -> str | None:
        reference: dict | None = self.request('GET', f'git/ref/tags/{tag}', missing=True)
        if reference is None:
            return None
        target: dict = reference['object']
        for _ in range(8):
            if target['type'] == 'commit':
                return target['sha']
            if target['type'] != 'tag':
                break
            target = self.request('GET', f"git/tags/{target['sha']}")['object']
        raise ValueError(f'{tag} does not resolve to a commit')

    def assets(self, release_id: int) -> list[dict]:
        result: list[dict] = []
        page: int = 1
        while True:
            batch: list[dict] = self.request('GET', f'releases/{release_id}/assets?per_page=100&page={page}')
            result.extend(batch)
            if len(batch) < 100:
                return result
            page += 1


def resolve_dependency(repository: str, reference: str, token: str) -> str:
    commit: dict = GitHub(repository, token).request('GET', f'commits/{urllib.parse.quote(reference, safe="")}')
    sha: str = commit['sha']
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise ValueError(f'{repository} did not resolve to a commit')
    return sha


def release_metadata(existing: dict) -> dict:
    match: re.Match | None = re.search(r'<!-- release: (.+) -->', existing.get('body') or '')
    if match is None:
        raise ValueError('Existing release has no publication identity; refusing to overwrite it')
    return json.loads(match[1])


def verify_assets(existing: list[dict], assets: dict[str, bytes]) -> bool:
    return {entry['name']: entry.get('digest') for entry in existing} == {
        name: 'sha256:' + hashlib.sha256(data).hexdigest() for name, data in assets.items()}


def publish(github: GitHub, config: dict, release: dict, assets: dict[str, bytes]) -> str:
    tag: str = release['tag']
    beta: bool = release['channel'] == 'beta'
    existing: dict | None = github.request('GET', f'releases/tags/{tag}', missing=True)
    commit: str | None = github.tag_commit(tag)
    if not beta and commit is not None and commit != release['sha']:
        raise ValueError(f'{tag} already points to another commit')
    if existing is not None:
        metadata: dict = release_metadata(existing)
        if bool(existing['prerelease']) != beta:
            raise ValueError('Existing release belongs to a different release channel')
        if beta and (metadata['run'], metadata['attempt']) > (release['run'], release['attempt']):
            return 'Skipped older beta run'
        if not beta and metadata['sha'] != release['sha']:
            raise ValueError('Stable release belongs to another commit')
        if not existing['draft'] and commit == release['sha'] and metadata['version'] == release['version']:
            if verify_assets(github.assets(existing['id']), assets):
                return 'Release already published with identical artifacts'
            if not beta:
                raise ValueError('Published release artifacts differ; refusing to overwrite them')
        if existing.get('immutable'):
            raise ValueError('Rolling beta requires mutable releases in repository settings')
        if not beta and not existing['draft']:
            raise ValueError('Stable release is already published')
    elif beta and commit is not None:
        raise ValueError('Beta tag exists without a release; refusing to move it')
    identity: dict = {key: release[key] for key in ('version', 'sha', 'run', 'attempt', 'volmlib_ref', 'wormholes_ref') if key in release}
    platform: str = 'Modded' if config['platform'] == 'modded' else 'CraftBukkit'
    body: str = (f"{platform} jars for {config['name']} {release['version']}.\n\n"
                 f"Commit: `{release['sha']}`\n\n"
                 f"<!-- release: {json.dumps(identity, sort_keys=True)} -->")
    attributes: dict = {
        'tag_name': tag, 'target_commitish': release['sha'],
        'name': f"{config['name']} {platform} {'Beta' if beta else release['version']}",
        'body': body, 'prerelease': beta, 'make_latest': 'false',
    }
    if existing is None:
        existing = github.request('POST', 'releases', {**attributes, 'draft': True})
    elif beta:
        existing = github.request('PATCH', f"releases/{existing['id']}", {**attributes, 'draft': True})
    uploaded: list[dict] = github.assets(existing['id'])
    by_name: dict[str, dict] = {entry['name']: entry for entry in uploaded}
    for name, data in assets.items():
        digest: str = 'sha256:' + hashlib.sha256(data).hexdigest()
        if name in by_name:
            if by_name[name].get('state') == 'starter':
                github.request('DELETE', f"releases/assets/{by_name[name]['id']}")
            elif by_name[name].get('digest') != digest:
                raise ValueError(f'Existing asset differs: {name}')
            else:
                continue
        upload_url: str = existing['upload_url'].split('{')[0] + '?' + urllib.parse.urlencode({'name': name})
        receipt: dict = github.request('POST', upload_url, data)
        if receipt.get('digest') != digest or receipt['name'] != name:
            raise ValueError(f'Upload verification failed: {name}')
    for entry in uploaded:
        if entry['name'] not in assets:
            github.request('DELETE', f"releases/assets/{entry['id']}")
    commit = github.tag_commit(tag)
    if commit is None:
        github.request('POST', 'git/refs', {'ref': f'refs/tags/{tag}', 'sha': release['sha']})
    elif commit != release['sha']:
        if not beta:
            raise ValueError('Stable tag changed during publication')
        github.request('PATCH', f'git/refs/tags/{tag}', {'sha': release['sha'], 'force': True})
    if not beta and config['platform'] == 'bukkit':
        latest: dict | None = github.request('GET', 'releases/latest', missing=True)
        match: re.Match | None = re.match(r'^v?(\d+)\.(\d+)\.(\d+)(?:$|-)', latest['tag_name']) if latest else None
        if latest is None or (match and tuple(map(int, release['version'].split('.'))) > tuple(map(int, match.groups()))):
            attributes['make_latest'] = 'true'
    github.request('PATCH', f"releases/{existing['id']}", {**attributes, 'draft': False})
    return f"Published {tag} at {release['sha']}"


def main() -> None:
    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['plan', 'publish'])
    parser.add_argument('--artifacts', type=Path)
    arguments: argparse.Namespace = parser.parse_args()
    root: Path = Path(__file__).resolve().parent.parent
    config: dict = read_config(root)
    event: dict = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    environment: dict = dict(os.environ)
    if arguments.command == 'publish':
        environment['GITHUB_RUN_ATTEMPT'] = environment['RELEASE_ATTEMPT']
    release: dict = plan(root, config, event, environment)
    if arguments.command == 'plan':
        if release['channel'] != 'none':
            release['volmlib_ref'] = resolve_dependency('VolmitSoftware/VolmLib', config['volmlibRef'], environment['GH_TOKEN'])
            if release['wormholes_ref']:
                release['wormholes_ref'] = resolve_dependency('VolmitSoftware/Wormholes', config['wormholesRef'], environment['GH_TOKEN'])
        with Path(os.environ['GITHUB_OUTPUT']).open('a') as output:
            for key, value in release.items():
                output.write(f'{key}={json.dumps(value, separators=(",", ":")) if isinstance(value, dict) else value}\n')
        print(f"Release channel: {release['channel']}")
    elif release['channel'] != 'none':
        if arguments.artifacts is None:
            raise ValueError('Publication requires the complete artifact directory')
        for key in ('volmlib_ref', 'wormholes_ref'):
            if release[key]:
                resolved: str = environment[f'RELEASE_{key.upper()}']
                if not re.fullmatch(r'[a-f0-9]{40}', resolved):
                    raise ValueError(f'{key} is not a resolved dependency commit')
                release[key] = resolved
        github: GitHub = GitHub(os.environ['GITHUB_REPOSITORY'], os.environ['GH_TOKEN'])
        print(publish(github, config, release, collect_artifacts(arguments.artifacts, release)))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError) as error:
        print(f'::error::{error}', file=sys.stderr)
        sys.exit(1)
