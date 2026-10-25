package main

import (
	shared "clashmanager/configyaml"
	"fmt"
	"github.com/dlclark/regexp2"
	MC "github.com/metacubex/mihomo/constant"
	"gopkg.in/yaml.v3"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// A read-only projection: never instantiate adapters, providers, health checks or TUN.
// Only presentation fields leave this function; credentials remain in the profile.
func previewProxies(r request) (any, error) {
	enhanced, err := shared.ApplyProfileExtensionChain([]byte(r.YAML), r.Name, r.Global, r.Extensions)
	if err != nil {
		return nil, fmt.Errorf("无法合并配置预览")
	}
	var doc map[string]any
	if yaml.Unmarshal(enhanced, &doc) != nil {
		return nil, fmt.Errorf("无法读取配置预览")
	}
	result := map[string]any{}
	builtin := map[string]string{"DIRECT": "Direct", "REJECT": "Reject", "REJECT-DROP": "RejectDrop", "PASS": "Pass", "COMPATIBLE": "Compatible"}
	for n, t := range builtin {
		result[n] = map[string]any{"type": t}
	}
	direct := []string{}
	for _, raw := range previewList(doc["proxies"]) {
		p, _ := raw.(map[string]any)
		n := previewString(p["name"])
		if n != "" {
			result[n] = map[string]any{"type": previewString(p["type"])}
			direct = append(direct, n)
		}
	}
	providers, _ := doc["proxy-providers"].(map[string]any)
	providerNames := make([]string, 0, len(providers))
	for n := range providers {
		providerNames = append(providerNames, n)
	}
	sort.Strings(providerNames)
	members := map[string][]string{}
	pending := []string{}
	for _, n := range providerNames {
		p, _ := providers[n].(map[string]any)
		payload := previewList(p["payload"])
		ok := previewString(p["type"]) == "inline"
		if !ok {
			payload, ok = previewCachedProvider(p)
		}
		override, _ := p["override"].(map[string]any)
		// Expressions can change arbitrary fields. Do not claim to resolve these offline.
		if len(previewList(override["override-expr"])) > 0 {
			ok = false
		}
		if !ok {
			pending = append(pending, n)
			continue
		}
		for _, raw := range payload {
			node, _ := raw.(map[string]any)
			name := previewString(node["name"])
			kind := previewString(node["type"])
			match, e := previewMatch(name, kind, p)
			if e != nil {
				ok = false
				break
			}
			if !match || name == "" {
				continue
			}
			for _, rawRule := range previewList(override["proxy-name"]) {
				rule, _ := rawRule.(map[string]any)
				re, e := regexp2.Compile(previewString(rule["pattern"]), regexp2.None)
				if e != nil {
					ok = false
					break
				}
				re.MatchTimeout = 20 * time.Millisecond
				name, e = re.Replace(name, previewString(rule["target"]), 0, -1)
				if e != nil {
					ok = false
					break
				}
			}
			if !ok {
				break
			}
			name = previewString(override["additional-prefix"]) + name + previewString(override["additional-suffix"])
			members[n] = append(members[n], name)
			result[name] = map[string]any{"type": kind}
		}
		if !ok {
			members[n] = nil
			pending = append(pending, n)
		}
	}
	groupNames := []string{}
	for _, raw := range previewList(doc["proxy-groups"]) {
		g, _ := raw.(map[string]any)
		name := previewString(g["name"])
		if name == "" {
			continue
		}
		groupNames = append(groupNames, name)
		names := previewStrings(g["proxies"])
		if g["include-all"] == true || g["include-all-proxies"] == true {
			for _, n := range direct {
				match, e := previewMatch(n, "", map[string]any{"filter": g["filter"]})
				if e != nil {
					return nil, fmt.Errorf("配置筛选表达式无法预览")
				}
				if match {
					names = append(names, n)
				}
			}
		}
		use := previewStrings(g["use"])
		if g["include-all"] == true || g["include-all-providers"] == true {
			use = providerNames
		}
		for _, p := range use {
			for _, n := range members[p] {
				match, e := previewMatch(n, "", map[string]any{"filter": g["filter"]})
				if e != nil {
					return nil, fmt.Errorf("配置筛选表达式无法预览")
				}
				if match {
					names = append(names, n)
				}
			}
		}
		selected := []string{}
		seen := map[string]bool{}
		for _, n := range names {
			node, _ := result[n].(map[string]any)
			match, e := previewMatch(n, previewString(node["type"]), map[string]any{"exclude-filter": g["exclude-filter"], "exclude-type": g["exclude-type"]})
			if e != nil {
				return nil, fmt.Errorf("配置筛选表达式无法预览")
			}
			if match && !seen[n] {
				selected = append(selected, n)
				seen[n] = true
			}
		}
		typ := map[string]string{"select": "Selector", "url-test": "URLTest", "fallback": "Fallback", "load-balance": "LoadBalance", "relay": "Relay"}[previewString(g["type"])]
		if typ == "" {
			typ = previewString(g["type"])
		}
		result[name] = map[string]any{"type": typ, "all": selected}
	}
	if _, exists := result["GLOBAL"]; !exists {
		all := append([]string{"DIRECT", "REJECT"}, direct...)
		all = append(all, groupNames...)
		result["GLOBAL"] = map[string]any{"type": "Selector", "all": all}
	}
	return map[string]any{"proxies": result, "pendingProviders": pending}, nil
}
func previewString(v any) string { s, _ := v.(string); return s }
func previewList(v any) []any    { s, _ := v.([]any); return s }
func previewStrings(v any) []string {
	out := []string{}
	for _, x := range previewList(v) {
		if s := previewString(x); s != "" {
			out = append(out, s)
		}
	}
	return out
}
func previewMatch(name, kind string, p map[string]any) (bool, error) {
	for _, test := range []struct {
		key     string
		exclude bool
	}{{"filter", false}, {"exclude-filter", true}} {
		pattern := previewString(p[test.key])
		if pattern == "" {
			continue
		}
		matched := false
		for _, part := range strings.Split(pattern, "`") {
			re, e := regexp2.Compile(part, regexp2.None)
			if e != nil {
				return false, e
			}
			re.MatchTimeout = 20 * time.Millisecond
			m, e := re.MatchString(name)
			if e != nil {
				return false, e
			}
			matched = matched || m
		}
		if matched == test.exclude {
			return false, nil
		}
	}
	for _, t := range strings.Split(previewString(p["exclude-type"]), "|") {
		if t != "" && strings.EqualFold(t, kind) {
			return false, nil
		}
	}
	return true, nil
}
func previewCachedProvider(p map[string]any) ([]any, bool) {
	path := previewString(p["path"])
	if path == "" && previewString(p["type"]) == "http" {
		path = MC.Path.GetPathByHash("proxies", previewString(p["url"]))
	}
	if path == "" {
		return nil, false
	}
	home := MC.Path.HomeDir()
	if filepath.IsAbs(path) {
		var e error
		path, e = filepath.Rel(home, path)
		if e != nil {
			return nil, false
		}
	}
	// Root confines symlink resolution and .. traversal to the app's private core home.
	root, e := os.OpenRoot(home)
	if e != nil {
		return nil, false
	}
	defer root.Close()
	file, e := root.Open(path)
	if e != nil {
		return nil, false
	}
	defer file.Close()
	info, e := file.Stat()
	if e != nil || !info.Mode().IsRegular() || info.Size() > 8<<20 {
		return nil, false
	}
	data, e := io.ReadAll(io.LimitReader(file, (8<<20)+1))
	if e != nil || len(data) > 8<<20 {
		return nil, false
	}
	var doc map[string]any
	if yaml.Unmarshal(data, &doc) != nil {
		return nil, false
	}
	list, ok := doc["proxies"].([]any)
	return list, ok
}
