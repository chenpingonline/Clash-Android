#!/usr/bin/env python3
"""Export the pinned, unmodified shared configuration package for the mobile build."""
from pathlib import Path
import os, subprocess

root = Path(__file__).resolve().parents[1]
revision = '261050fe9b6c1d64ae387be5900228429b9816bd'
source = Path(os.environ.get('CLASH_MANAGER_SOURCE', str(root.parents[1] / 'Clash-Manager')))
if not (source / '.git').exists():
    source = root / '.cache' / 'shared-source'
    if not (source / '.git').exists():
        subprocess.run(['git', 'clone', '--filter=blob:none', 'https://github.com/chenpingonline/Clash-Manager.git', str(source)], check=True)
subprocess.run(['git', '-C', str(source), 'cat-file', '-e', revision + '^{commit}'], check=True)
prefix = 'backend/internal/configyaml/'
paths = subprocess.check_output(['git', '-C', str(source), 'ls-tree', '-r', '--name-only', revision, prefix], text=True).splitlines()
dest = root / '.cache' / 'shared-configyaml'
dest.mkdir(parents=True, exist_ok=True)
for path in paths:
    if path.endswith('.go'):
        (dest / Path(path).name).write_bytes(subprocess.check_output(['git', '-C', str(source), 'show', f'{revision}:{path}']))
(dest / 'LICENSE').write_bytes(subprocess.check_output(['git', '-C', str(source), 'show', f'{revision}:LICENSE']))
(dest / 'go.mod').write_text('module clashmanager/configyaml\n\ngo 1.25.0\n\nrequire (\n github.com/dop251/goja v0.0.0-20251201205617-2bb4c724c0f9\n gopkg.in/yaml.v3 v3.0.1\n)\n')
print(f'Exported unmodified configyaml at {revision}')
# Rule import is exported from the same revision; only the internal import path is adapted.
rule_dest = root / '.cache' / 'shared-ruleimport'
rule_dest.mkdir(parents=True, exist_ok=True)
rule_paths = subprocess.check_output(['git','-C',str(source),'ls-tree','-r','--name-only',revision,'backend/internal/ruleimport/'],text=True).splitlines()
for path in rule_paths:
    if path.endswith('.go'):
        data=subprocess.check_output(['git','-C',str(source),'show',f'{revision}:{path}']).decode().replace('github.com/chenpingonline/Clash-Manager/backend/internal/configyaml','clashmanager/configyaml')
        (rule_dest / Path(path).name).write_text(data)
(rule_dest / 'go.mod').write_text('module clashmanager/ruleimport\n\ngo 1.25.0\n')
