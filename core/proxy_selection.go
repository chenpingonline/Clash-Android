package main

import (
	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/config"
)

// Android owns encrypted, per-profile choices. The core's global group-name cache
// would otherwise replace them (including choices from a different profile).
// ForceSet before ApplyConfig also supports providers initialized during loading.
func prepareSelections(cfg *config.Config, selections map[string]string) {
	cfg.Profile.StoreSelected = false
	for name, selected := range selections {
		if proxy := cfg.Proxies[name]; proxy != nil {
			if selector, ok := proxy.Adapter().(*outboundgroup.Selector); ok {
				selector.ForceSet(selected)
			}
		}
	}
}
func invalidSelections(cfg *config.Config, selections map[string]string) int {
	invalid := 0
	for name, selected := range selections {
		proxy := cfg.Proxies[name]
		if proxy == nil {
			invalid++
			continue
		}
		selector, ok := proxy.Adapter().(*outboundgroup.Selector)
		if !ok || selector.Now() != selected {
			invalid++
		}
	}
	return invalid
}
