#!/usr/bin/env python3
"""Compose PostgreSQL custom-format backup and restore into a NEW database only."""
import argparse
import os
from pathlib import Path
import re
import subprocess


def archive(compose, action, path, database=None):
    prefix = compose + ['exec', '-T', 'postgres']
    if action == 'backup':
        # Exclusive creation prevents overwriting an earlier backup; partial failures are removed.
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, 'wb') as output:
                subprocess.run(prefix + ['sh', '-c', 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl'],
                               stdout=output, check=True)
        except BaseException:
            Path(path).unlink()
            raise
    else:
        if not database or not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', database):
            raise ValueError('new database must match [a-z][a-z0-9_]{0,62}')
        with open(path, 'rb') as source:
            # createdb fails if the target already exists: NEVER restore over existing data.
            subprocess.run(prefix + ['sh', '-c', 'exec createdb -U "$POSTGRES_USER" -T template0 "$1"', 'restore', database], check=True)
            subprocess.run(prefix + ['sh', '-c', 'exec pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-acl --single-transaction --exit-on-error', 'restore', database],
                           stdin=source, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compose-file', default='docker-compose.quick-test.yml')
    parser.add_argument('--project', default='youthletter-quick')
    parser.add_argument('--env-file')
    parser.add_argument('action', choices=['backup', 'restore'])
    parser.add_argument('path', type=Path)
    parser.add_argument('--new-database')
    args = parser.parse_args()
    command = ['docker', 'compose', '-p', args.project, '-f', args.compose_file]
    if args.env_file:
        command += ['--env-file', args.env_file]
    try:
        archive(command, args.action, args.path, args.new_database)
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f'{args.action} failed ({type(exc).__name__}). For restore, inspect the new database before retrying; existing databases were not replaced.\n')
    print(f'{args.action} completed: {args.path}')


if __name__ == '__main__':
    main()
