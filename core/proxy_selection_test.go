package main

import (
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/config"
	"testing"
)

func TestInitialSelectionBeforeRuntimeAndInvalidFallback(t *testing.T) {
	raw := []byte(`proxies:
 - {name: First, type: direct}
 - {name: Chosen, type: direct}
proxy-groups:
 - {name: Manual, type: select, proxies: [First, Chosen]}
 - {name: Auto, type: url-test, proxies: [First, Chosen], url: 'http://127.0.0.1/', interval: 300}
profile: {store-selected: true}
rules: ['MATCH,Manual']
`)
	cfg, e := config.Parse(raw)
	if e != nil {
		t.Fatal(e)
	}
	choices := map[string]string{"Manual": "Chosen", "Auto": "Chosen"}
	prepareSelections(cfg, choices)
	manual := cfg.Proxies["Manual"].Adapter().(*outboundgroup.Selector)
	if manual.Now() != "Chosen" {
		t.Fatal("startup selection was not installed before ApplyConfig")
	}
	if cfg.Profile.StoreSelected {
		t.Fatal("global cache could override per-profile selection")
	}
	if invalidSelections(cfg, choices) != 1 {
		t.Fatal("non-manual group accepted")
	}
	prepareSelections(cfg, map[string]string{"Manual": "Removed"})
	if manual.Now() != "First" {
		t.Fatal("removed choice did not fall back safely")
	}
	if invalidSelections(cfg, map[string]string{"Manual": "Removed", "DeletedGroup": "First"}) != 2 {
		t.Fatal("invalid choices not reported")
	}
}
