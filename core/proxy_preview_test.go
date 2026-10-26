package main

import (
	"encoding/json"
	MC "github.com/metacubex/mihomo/constant"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
)

func TestPreviewWithoutRuntime(t *testing.T) {
	var hits atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { hits.Add(1) }))
	defer server.Close()
	home := t.TempDir()
	old := MC.Path.HomeDir()
	MC.SetHomeDir(home)
	defer MC.SetHomeDir(old)
	os.WriteFile(filepath.Join(home, "cache.yaml"), []byte("proxies:\n - {name: Cache HK, type: ss, password: PRIVATE}\n"), 0600)
	raw := `proxies:
 - {name: HK, type: ss, password: SECRET, server: secret.example, port: 443, cipher: aes-128-gcm}
proxy-providers:
 cached: {type: file, path: cache.yaml}
 missing: {type: http, url: "` + server.URL + `/SECRET"}
 inline:
  type: inline
  payload:
   - {name: JP, type: direct}
  override: {additional-prefix: "Inline "}
proxy-groups:
 - {name: Manual, type: select, proxies: [HK, DIRECT], use: [cached, missing, inline]}
 - {name: Auto, type: url-test, include-all: true, filter: "(?=HK).*"}
`
	value, e := previewProxies(request{YAML: raw})
	if e != nil {
		t.Fatal(e)
	}
	result := value.(map[string]any)
	proxies := result["proxies"].(map[string]any)
	if got := strings.Join(result["groupOrder"].([]string), ","); got != "Manual,Auto" {
		t.Fatal(got)
	}
	names := proxies["Manual"].(map[string]any)["all"].([]string)
	if strings.Join(names, ",") != "HK,DIRECT,Cache HK,Inline JP" {
		t.Fatal(names)
	}
	auto := proxies["Auto"].(map[string]any)["all"].([]string)
	if strings.Join(auto, ",") != "HK,Cache HK" {
		t.Fatal(auto)
	}
	if proxies["Manual"].(map[string]any)["now"] != nil {
		t.Fatal("preview invents runtime selection")
	}
	if strings.Join(result["pendingProviders"].([]string), ",") != "missing" {
		t.Fatal(result)
	}
	encoded, _ := json.Marshal(value)
	for _, secret := range []string{"SECRET", "PRIVATE", "secret.example", server.URL} {
		if strings.Contains(string(encoded), secret) {
			t.Fatal("preview exposed private configuration")
		}
	}
	if hits.Load() != 0 {
		t.Fatal("preview fetched a provider")
	}
}
func TestPreviewConfinesCacheAndAppliesEnhancements(t *testing.T) {
	home := t.TempDir()
	old := MC.Path.HomeDir()
	MC.SetHomeDir(home)
	defer MC.SetHomeDir(old)
	outside := filepath.Join(t.TempDir(), "outside.yaml")
	os.WriteFile(outside, []byte("proxies: [{name: Escaped, type: direct}]"), 0600)
	os.Symlink(outside, filepath.Join(home, "escape.yaml"))
	raw := `proxies: [{name: Original, type: direct}]
proxy-providers:
 escape: {type: file, path: escape.yaml}
proxy-groups: [{name: Group, type: select, proxies: [Original], use: [escape]}]
`
	value, e := previewProxies(request{YAML: raw, Extensions: map[string]string{"override": "proxies:\n - {name: Enhanced, type: direct}\nproxy-groups:\n - {name: EnhancedGroup, type: select, proxies: [Enhanced], use: [escape]}"}})
	if e != nil {
		t.Fatal(e)
	}
	result := value.(map[string]any)
	proxies := result["proxies"].(map[string]any)
	if got := strings.Join(result["groupOrder"].([]string), ","); got != "EnhancedGroup" {
		t.Fatal(got)
	}
	if proxies["Enhanced"] == nil || proxies["Original"] != nil || proxies["Escaped"] != nil {
		t.Fatal(proxies)
	}
	if len(result["pendingProviders"].([]string)) != 1 {
		t.Fatal(result)
	}
}

func TestRuntimeGroupOrder(t *testing.T) {
	doc := map[string]any{"proxy-groups": []any{
		map[string]any{"name": "Zebra"}, map[string]any{"name": "Alpha"}, map[string]any{"name": "GLOBAL"},
	}}
	if got := strings.Join(proxyGroupOrder(doc), ","); got != "Zebra,Alpha,GLOBAL" {
		t.Fatal(got)
	}
	if len(proxyGroupOrder(nil)) != 0 {
		t.Fatal("empty config invented groups")
	}
}
