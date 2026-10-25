package main

/*
#include <stdlib.h>
*/
import "C"

import (
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "os"
    "net/netip"
    "path/filepath"
    "strings"
    "sync"
    "sync/atomic"
    "time"
    shared "clashmanager/configyaml"
    ri "clashmanager/ruleimport"
    "golang.org/x/sys/unix"

    "github.com/metacubex/mihomo/config"
    "github.com/metacubex/mihomo/component/geodata"
    "github.com/metacubex/mihomo/component/updater"
    "github.com/metacubex/mihomo/component/resolver"
    MC "github.com/metacubex/mihomo/constant"
    "github.com/metacubex/mihomo/hub"
    "github.com/metacubex/mihomo/hub/executor"
    "github.com/metacubex/mihomo/hub/route"
    "github.com/metacubex/mihomo/listener"
    LC "github.com/metacubex/mihomo/listener/config"
    "github.com/metacubex/mihomo/dns"
    "github.com/metacubex/mihomo/tunnel"
    "github.com/metacubex/mihomo/tunnel/statistic"
    "github.com/metacubex/mihomo/log"
    logrus "github.com/sirupsen/logrus"
    "gopkg.in/yaml.v3"
)

var lock sync.Mutex
var running bool
var logsMu sync.Mutex
var logs []string
var tunFailure string
var retainedLogLevel atomic.Int32
// Only platform-owned TUN failures are captured; configuration/traffic logs remain redacted.
type tunErrorHook struct{}
func (tunErrorHook) Levels() []logrus.Level { return []logrus.Level{logrus.ErrorLevel} }
func (tunErrorHook) Fire(e *logrus.Entry) error {
    if strings.HasPrefix(e.Message,"Start TUN listening error:") { logsMu.Lock(); tunFailure=strings.TrimSpace(e.Message); logsMu.Unlock() }
    return nil
}

type request struct {
    Op string `json:"op"`
    Home string `json:"home"`
    YAML string `json:"yaml"`
    FD int `json:"fd"`
    Secret string `json:"secret"`
    Selections map[string]string `json:"selections"`
    Global map[string]string `json:"global"`
    Extensions map[string]string `json:"extensions"`
    Name string `json:"name"`
    Section string `json:"section"`
    Action string `json:"action"`
    Value string `json:"value"`
    IPv6 bool `json:"ipv6"`
    MTU int `json:"mtu"`
    Data string `json:"data"`
    Options ri.Options `json:"options"`
}

func init() {
    retainedLogLevel.Store(int32(log.INFO))
    logrus.SetOutput(io.Discard)
    logrus.AddHook(tunErrorHook{})
    sub := log.Subscribe()
    go func() {
        for event := range sub {
            if int32(event.LogLevel)<retainedLogLevel.Load(){continue}
            msg := time.Now().Format("15:04:05") + " · " + event.Type() + " · " + redactLog(event.Payload)

            logsMu.Lock()
            logs = append(logs, msg)
            if len(logs)>100 { logs=logs[len(logs)-100:] }
            logsMu.Unlock()
        }
    }()
}

// mobileConfig isolates platform-owned listeners from imported YAML.
func mobileConfig(raw string, fd int, secret string) ([]byte,error) {
    if len(raw)>8*1024*1024 { return nil, errors.New("配置不能超过8 MiB") }
    m:=map[string]any{}
    if err:=yaml.Unmarshal([]byte(raw),&m);err!=nil {return nil,err}
    if len(m)==0 {return nil,errors.New("配置不能为空")}
    for _,key:=range []string{"external-controller-tls","external-controller-unix","external-controller-pipe","external-ui","external-ui-url","external-ui-name","interface-name","routing-mark","listeners","tunnels","iptables","ntp"} {delete(m,key)}
    m["external-controller"]="127.0.0.1:19097"
    delete(m,"ss-config");delete(m,"vmess-config");delete(m,"tuic-server")
    delete(m,"external-doh-server");delete(m,"external-controller-cors");delete(m,"external-controller-routing-mark")
    m["secret"]=secret
    if _,ok:=m["allow-lan"];!ok{m["allow-lan"]=false}
    if _,ok:=m["bind-address"];!ok{m["bind-address"]="127.0.0.1"}
    m["redir-port"]=0; m["tproxy-port"]=0
    if _,ok:=m["ipv6"];!ok {m["ipv6"]=false}
    m["tun"]=map[string]any{"enable":fd>0,"file-descriptor":fd,"device":"ClashAndroid","stack":"gvisor","auto-route":false,"auto-detect-interface":false,"mtu":1500,"dns-hijack":[]string{"any:53"},"inet4-address":[]string{"172.19.0.1/30"}}
    dns,ok:=m["dns"].(map[string]any)
    if !ok {dns=map[string]any{}}
    dns["enable"]=true;dns["listen"]="127.0.0.1:0";dns["ipv6"]=m["ipv6"]
    if _,ok:=dns["nameserver"];!ok {dns["nameserver"]=[]string{"1.1.1.1","223.5.5.5"}}
    if _,ok:=dns["enhanced-mode"];!ok {dns["enhanced-mode"]="redir-host"}
    if m["ipv6"]==true { m["tun"].(map[string]any)["inet6-address"]=[]string{"fdfe:dcba:9876::1/126"} }
    m["dns"]=dns
    return yaml.Marshal(m)
}

func dispatch(r request)(any,error) {
    switch r.Op {
    case "connectivity":
        return connectivity(r.Value)
    case "import-rules":
        return importRules(r)
    case "network-changed":
        resolver.ResetConnection();return "updated",nil
    case "log-level":
        var level log.LogLevel;if err:=level.UnmarshalText([]byte(r.Value));err!=nil{return nil,err};log.SetLevel(level);retainedLogLevel.Store(int32(level));return level.String(),nil
    case "mode":
        if !running{return nil,errors.New("VPN未启动")};var mode tunnel.TunnelMode;if err:=mode.UnmarshalText([]byte(r.Value));err!=nil{return nil,err};tunnel.SetMode(mode);return mode.String(),nil
    case "geo-status":
        result:=[]map[string]any{};for name,path:=range map[string]string{"GeoIP":MC.Path.GeoIP(),"GeoSite":MC.Path.GeoSite(),"MMDB":MC.Path.MMDB(),"ASN":MC.Path.ASN()}{item:=map[string]any{"name":name,"exists":false};if info,err:=os.Stat(path);err==nil{item["exists"]=true;item["bytes"]=info.Size();item["updated"]=info.ModTime().UnixMilli()};result=append(result,item)};return result,nil
    case "geo-update":
        if !running{return nil,errors.New("请先启动VPN以加载GEO来源")};if !geodata.GeoIpEnable() && !geodata.GeoSiteEnable() && !geodata.ASNEnable(){return nil,errors.New("当前配置未使用 GEO 资源，无需更新")};return "updated",updater.UpdateGeoDatabases()
    case "rule-disable":
        if !running{return nil,errors.New("VPN未启动")};var changes map[int]bool;if err:=json.Unmarshal([]byte(r.Value),&changes);err!=nil{return nil,err};rules:=tunnel.Rules();for index:=range changes{if index<0||index>=len(rules){return nil,errors.New("规则索引失效，请刷新")};if _,ok:=rules[index].(MC.RuleWrapper);!ok{return nil,errors.New("当前规则不支持运行时开关")}};for index,disabled:=range changes{rules[index].(MC.RuleWrapper).SetDisabled(disabled)};return "updated",nil
    case "decode":
        var value any;err:=yaml.Unmarshal([]byte(r.Value),&value);return value,err
    case "encode":
        var value any;if err:=json.Unmarshal([]byte(r.Value),&value);err!=nil{return nil,err};b,err:=yaml.Marshal(value);return string(b),err
    case "preview-proxies":
        return previewProxies(r)
    case "section":
        return sectionDocument(r)
    case "init":
        if r.Home=="" {return nil,errors.New("缺少私有数据目录")}
        if err:=os.MkdirAll(r.Home,0700);err!=nil{return nil,err}
        MC.SetHomeDir(r.Home)
        MC.SetConfig(filepath.Join(r.Home,"config.yaml"))
        route.SetEmbedMode(true)
        return "Mihomo v1.19.30",config.Init(r.Home)
    case "validate":
        enhanced,err:=shared.ApplyProfileExtensionChain([]byte(r.YAML),r.Name,r.Global,r.Extensions);if err!=nil{return nil,err}
        raw,err:=mobileConfig(string(enhanced),0,"validation-only");if err!=nil{return nil,err}
        if err=checkConfiguration(raw,r.Op=="start");err!=nil{return nil,err}
        cfg,err:=executor.ParseWithBytes(raw);if err!=nil{return nil,err}
        return map[string]any{"proxies":len(cfg.Proxies),"rules":len(cfg.Rules)},nil
    case "render":
        enhanced,err:=shared.ApplyProfileExtensionChain([]byte(r.YAML),r.Name,r.Global,r.Extensions)
        if err!=nil{return nil,err};return string(enhanced),nil
    case "start":
        if running {return nil,errors.New("请先停止当前VPN")}
        if r.FD<=0 || len(r.Secret)<24 {return nil,errors.New("无效的VPN句柄或控制器认证")}
        enhanced,err:=shared.ApplyProfileExtensionChain([]byte(r.YAML),r.Name,r.Global,r.Extensions);if err!=nil{return nil,err}
        raw,err:=mobileConfig(string(enhanced),r.FD,r.Secret);if err!=nil{return nil,err}
        var platform map[string]any;_ = yaml.Unmarshal(raw,&platform);platform["ipv6"]=r.IPv6;platform["dns"].(map[string]any)["ipv6"]=r.IPv6
        if r.IPv6{platform["tun"].(map[string]any)["inet6-address"]=[]string{"fdfe:dcba:9876::1/126"}}else{delete(platform["tun"].(map[string]any),"inet6-address")}
        raw,err=yaml.Marshal(platform);if err!=nil{return nil,err}
        if err=checkConfiguration(raw,r.Op=="start");err!=nil{return nil,err}
        cfg,err:=executor.ParseWithBytes(raw);if err!=nil{return nil,err}
        prepareSelections(cfg,r.Selections)
        ownedFD,err:=unix.Dup(r.FD);if err!=nil{return nil,err}
        cfg.General.Tun.FileDescriptor=ownedFD
        // The Android descriptor's addresses are authoritative; Mihomo's parser
        // otherwise derives these from the fake-IP range.
        cfg.General.Tun.Inet4Address=[]netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")}
        if r.IPv6{cfg.General.Tun.Inet6Address=[]netip.Prefix{netip.MustParsePrefix("fdfe:dcba:9876::1/126")}}else{cfg.General.Tun.Inet6Address=nil}
        if r.MTU!=0 {cfg.General.Tun.MTU=uint32(r.MTU)}
        logsMu.Lock();tunFailure="";logsMu.Unlock()
        geoAuto:=cfg.General.GeoAutoUpdate;geoInterval:=cfg.General.GeoUpdateInterval
        cfg.General.GeoAutoUpdate=false // Android Service owns periodic work and cancellation.
        retainedLogLevel.Store(int32(cfg.General.LogLevel))
        hub.ApplyConfig(cfg)
        if !listener.GetTunConf().Enable {stopRuntime();logsMu.Lock();reason:=tunFailure;logsMu.Unlock();return nil,fmt.Errorf("TUN启动失败: %s",reason)}
        running=true
        return map[string]any{"started":true,"geoAuto":geoAuto,"geoInterval":geoInterval,"invalidSelections":invalidSelections(cfg,r.Selections)},nil
    case "stop":
        stopRuntime()
        return "stopped",nil
    case "stats":
        up,down:=statistic.DefaultManager.Now()
        totalUp,totalDown:=statistic.DefaultManager.Total()
        return map[string]any{"running":running,"up":up,"down":down,"totalUp":totalUp,"totalDown":totalDown},nil
    case "logs":
        logsMu.Lock();defer logsMu.Unlock()
        return append([]string{},logs...),nil
    default:return nil,fmt.Errorf("未知操作：%s",r.Op)
    }
}

// Mihomo's process-oriented Shutdown only closes TUN. Embedded lifecycle must
// explicitly release every app-owned listener before a new start.
func stopRuntime(){
    tunnel.OnSuspend()
    statistic.DefaultManager.Range(func(c statistic.Tracker)bool{_ = c.Close();return true})
    listener.ReCreateHTTP(0,tunnel.Tunnel)
    listener.ReCreateSocks(0,tunnel.Tunnel)
    listener.ReCreateMixed(0,tunnel.Tunnel)
    listener.ReCreateTun(LC.Tun{},tunnel.Tunnel)
    dns.ReCreateServer("",nil,nil)
    executor.Shutdown()
    route.ReCreateServer(&route.Config{})
    running=false
}

//export ClashCall
func ClashCall(input *C.char) *C.char {
    lock.Lock();defer lock.Unlock()
    out:=map[string]any{}
    func(){
        defer func(){if recover()!=nil {out=map[string]any{"error":"内核操作异常"}}}()
        var r request
        if err:=json.Unmarshal([]byte(C.GoString(input)),&r);err!=nil {out["error"]="无效的桥接请求";return}
        result,err:=dispatch(r)
        if err!=nil {out["error"]=err.Error()} else {out["data"]=result}
    }()
    encoded,_:=json.Marshal(out)
    return C.CString(string(encoded))
}

func main() {}
