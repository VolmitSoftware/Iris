import argparse
import fcntl
import json
import os
from pathlib import Path
import statistics
import subprocess
import time


def fields(line):
    return dict(part.split('=', 1) for part in line.split()[1:])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pack', required=True)
    parser.add_argument('--dimension', required=True)
    parser.add_argument('--volmlib', required=True)
    parser.add_argument('--seed', type=int, default=1337)
    parser.add_argument('--center-x', type=int, default=2048)
    parser.add_argument('--center-z', type=int, default=2048)
    parser.add_argument('--warmup', type=int, default=64)
    parser.add_argument('--measured', type=int, default=192)
    parser.add_argument('--pairs', type=int, default=2)
    parser.add_argument('--diagnostics', action='store_true')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if args.pairs < 1:
        parser.error('--pairs must be positive')
    root = Path(__file__).resolve().parents[1]
    output = Path(args.output).resolve()
    if not any(output.is_relative_to(root / allowed) for allowed in ('.perf', 'build')):
        parser.error('--output must be inside this checkout\'s .perf or build directory')
    output.mkdir(parents=True, exist_ok=False)
    environment = os.environ.copy()
    environment.pop('GIT_CONFIG_COUNT', None)
    for variable in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(variable, None)
    results = []
    expected_hashes = None
    with open('/tmp/iris-acceleration-integration-benchmark.lock', 'a') as lock:
        print('Waiting for generation benchmark lock', flush=True)
        fcntl.flock(lock, fcntl.LOCK_EX)
        for pair in range(args.pairs):
            for native in ([False, True] if pair % 2 == 0 else [True, False]):
                backend = 'native' if native else 'java'
                command = [str(root / 'gradlew'), ':probe:genProbe', '--no-daemon',
                           '-PincludeNativeBillow=true', f'-PlocalVolmLibDirectory={args.volmlib}',
                           f'-PprobePack={Path(args.pack).resolve()}', f'-PprobeDimension={args.dimension}',
                           f'-PprobeSeed={args.seed}', f'-PprobeCenterChunkX={args.center_x}',
                           f'-PprobeCenterChunkZ={args.center_z}', f'-PprobeWarmupChunks={args.warmup}',
                           f'-PprobeMeasuredChunks={args.measured}', '-PprobeMulticore=false',
                           '-PprobeStudio=false', f'-PprobeNativeBillow={str(native).lower()}',
                           '-PprobeNativeAccess=true',
                           f'-PprobeNativeDiagnostics={str(args.diagnostics).lower()}',
                           f'-PprobeRequireNativeSamples={str(native and args.diagnostics).lower()}',
                           f'-PprobeExpectedBackend={"native" if native else "java-disabled"}']
                label = f'{pair + 1}-{backend}'
                (output / f'{label}.command.json').write_text(json.dumps(command, indent=2) + '\n')
                print(f'Running {label}', flush=True)
                started = time.monotonic()
                with (output / f'{label}.log').open('w') as log:
                    completed = subprocess.run(command, cwd=root, env=environment, stdout=log,
                                               stderr=subprocess.STDOUT, check=False)
                text = (output / f'{label}.log').read_text()
                lines = text.splitlines()
                hashes = [line for line in lines if line.startswith('GENPROBE_CHUNK_HASH ')]
                result_lines = [line for line in lines if line.startswith('IRIS_GENPROBE_RESULT ')]
                backend_lines = [line for line in lines if line.startswith('IRIS_GENPROBE_BACKEND ')]
                if completed.returncode != 0 or len(result_lines) != 1 or len(backend_lines) != 1:
                    raise RuntimeError(f'{label} failed; see {output / (label + ".log")}')
                result = fields(result_lines[0])
                status = fields(backend_lines[0])
                if result['status'] != 'PASS' or len(hashes) != args.warmup + args.measured:
                    raise RuntimeError(f'{label} did not generate every chunk successfully')
                expected_backend = 'native' if native else 'java-disabled'
                if status['status'] != expected_backend:
                    raise RuntimeError(f'{label} backend mismatch: {status}')
                if expected_hashes is None:
                    expected_hashes = hashes
                elif hashes != expected_hashes:
                    raise RuntimeError(f'{label} chunk block/biome hashes differ')
                results.append({'pair': pair + 1, 'backend': backend, 'result': result,
                                'backend_status': status, 'wall_seconds': time.monotonic() - started})
                (output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    rates = {backend: statistics.median(float(run['result']['measured_cps']) for run in results
                                      if run['backend'] == backend) for backend in ('java', 'native')}
    summary = {'median_chunks_per_second': rates,
               'native_throughput_change_percent': (rates['native'] / rates['java'] - 1) * 100,
               'all_chunk_hashes_equal': True, 'diagnostics_enabled': args.diagnostics,
               'run_count': len(results), 'seed': args.seed,
               'unique_chunks': args.warmup + args.measured}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
