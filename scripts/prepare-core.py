#!/usr/bin/env python3
"""Export pinned Mihomo and apply the narrow VpnService adaptation in build cache."""
import pathlib, subprocess, shutil
root = pathlib.Path(__file__).resolve().parents[1]
module = pathlib.Path(subprocess.check_output(['go','list','-m','-f','{{.Dir}}','github.com/metacubex/mihomo'],cwd=root/'core',text=True).strip())
source = module/'listener/sing_tun/server_android.go'
text = source.read_text()
needle = 'func (l *Listener) buildAndroidRules(tunOptions *tun.Options) error {\n'
assert text.count(needle) == 1, 'Mihomo Android adapter changed; review required'
text = text.replace(needle,needle+'\t// Android VpnService already applied per-app routing to this descriptor.\n\tif tunOptions.FileDescriptor > 0 { return nil }\n')
out = root/'.cache/android-core'
out.mkdir(parents=True,exist_ok=True)
export = out/'mihomo'
if not export.exists(): shutil.copytree(module,export)
adapted = export/'listener/sing_tun/server_android.go'
adapted.chmod(0o644)
adapted.write_text(text)
modfile=out/'mobile.mod'
shutil.copyfile(root/'core/go.mod',modfile)
shutil.copyfile(root/'core/go.sum',out/'mobile.sum')
subprocess.run(['go','mod','edit','-modfile',str(modfile),'-replace','github.com/metacubex/mihomo='+str(export)],cwd=root/'core',check=True)

# Resolve Android socket ownership once per new TUN flow, before Fake IP rewriting.
# The optional callback is absent on desktop/host builds and does not change their behavior.
owner_hook = export/'component/process/connection_owner.go'
owner_hook.parent.chmod(0o755)
if owner_hook.exists(): owner_hook.chmod(0o644)
owner_hook.write_text('''package process

import C "github.com/metacubex/mihomo/constant"

var DefaultConnectionOwnerResolver func(*C.Metadata)

func ResolveConnectionOwner(metadata *C.Metadata) {
    if resolver := DefaultConnectionOwnerResolver; resolver != nil {
        resolver(metadata)
    }
}
''')
tunnel_source = (module/'tunnel/tunnel.go').read_text()
for needle, replacement in [
    ('\tpreHandleFailed := false\n', '\tprocess.ResolveConnectionOwner(metadata)\n\tpreHandleFailed := false\n'),
    ('\t\t\toriginMetadata := metadata  // save origin metadata\n', '\t\t\tprocess.ResolveConnectionOwner(metadata)\n\t\t\toriginMetadata := metadata  // save origin metadata\n'),
]:
    assert tunnel_source.count(needle) == 1, 'Mihomo connection metadata changed; review required'
    tunnel_source = tunnel_source.replace(needle, replacement)
adapted_tunnel = export/'tunnel/tunnel.go'
adapted_tunnel.chmod(0o644)
adapted_tunnel.write_text(tunnel_source)
