package main

import (
    "testing"
    "strings"
    "os"
    "path/filepath"
    "gopkg.in/yaml.v3"
)

func TestInitUsesPrivateDirectory(t *testing.T) {
    home:=t.TempDir()
    if _,err:=dispatch(request{Op:"init",Home:home});err!=nil {t.Fatal(err)}
    if _,err:=os.Stat(filepath.Join(home,"config.yaml"));err!=nil {t.Fatal(err)}
}

func TestMobileConfigOwnsNetworkBoundaries(t *testing.T) {
    raw:=`external-controller: 0.0.0.0:9090
secret: imported-secret
allow-lan: true
mixed-port: 7890
listeners:
  - name: public
    type: socks
    port: 1080
tun:
  auto-route: true
  file-descriptor: 999
rules:
  - DOMAIN-SUFFIX,example.org,DIRECT
  - MATCH,REJECT
`
    encoded,err:=mobileConfig(raw,42,"session-secret");if err!=nil {t.Fatal(err)}
    var m map[string]any
    if err=yaml.Unmarshal(encoded,&m);err!=nil {t.Fatal(err)}
    if m["external-controller"]!="127.0.0.1:19097" || m["secret"]!="session-secret" {t.Fatal("imported controller escaped mobile policy")}
    if m["allow-lan"]!=true || m["mixed-port"]!=7890 {t.Fatal("explicit proxy settings were lost")}
    if _,ok:=m["listeners"];ok {t.Fatal("unmanaged listener retained")}
    tun:=m["tun"].(map[string]any)
    if tun["file-descriptor"]!=42 || tun["auto-route"]!=false {t.Fatal("tun ownership incorrect")}
    rules:=m["rules"].([]any)
    if len(rules)!=2 || rules[0]!="DOMAIN-SUFFIX,example.org,DIRECT" {t.Fatal("user rules changed")}
}

func TestMobileConfigRejectsEmptyAndInvalid(t *testing.T) {
    for _,raw:=range []string{"", "# empty", "[malformed", "- not-a-mapping"} {
        if _,err:=mobileConfig(raw,0,"validation");err==nil {t.Fatalf("accepted %q",raw)}
    }
}

func TestValidationDoesNotCreateTun(t *testing.T) {
    raw,err:=mobileConfig("rules: [MATCH,DIRECT]",0,"validation");if err!=nil {t.Fatal(err)}
    var m map[string]any;_ = yaml.Unmarshal(raw,&m)
    if m["tun"].(map[string]any)["enable"]!=false {t.Fatal("validation enabled TUN")}
}

func TestSectionEditPreservesOriginalAndReferences(t *testing.T) {
 original:="proxies: []\nproxy-groups: []\nrules:\n - MATCH,DIRECT\n"
 r:=request{Op:"section",Section:"rules",Action:"save",YAML:original,Value:"- DOMAIN-SUFFIX,example.org,REJECT\n- MATCH,DIRECT"}
 v,err:=dispatch(r);if err!=nil{t.Fatal(err)}
 ext:=v.(map[string]string)
 if !strings.Contains(ext["rules"],"delete:"){t.Fatal("original sequence exclusions missing")}
 if _,err=dispatch(request{Op:"validate",YAML:original,Extensions:ext});err!=nil{t.Fatal(err)}
 r.Value="- MATCH,missing-policy";v,err=dispatch(r);if err!=nil{t.Fatal(err)}
 if _,err=dispatch(request{Op:"validate",YAML:original,Extensions:v.(map[string]string)});err==nil{t.Fatal("missing policy accepted")}
}
func TestLogRedaction(t *testing.T) {
 result:=redactLog("download https://host/path?token=secret password=abc uuid=def")
 for _,value:=range []string{"host/path","abc","def"}{if strings.Contains(result,value){t.Fatal("sensitive value escaped")}}
}

func TestRejectPortConflictAndUnreachableRules(t *testing.T){
 for _,raw:=range []string{"mixed-port: 19097", "mixed-port: 7890\nsocks-port: 7890", "rules: [\"MATCH,DIRECT\", \"DOMAIN,example.org,REJECT\"]"}{if err:=checkConfiguration([]byte(raw),false);err==nil{t.Fatalf("accepted invalid configuration %s",raw)}}
}
