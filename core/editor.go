package main

import (
 "fmt"
 "strings"
 "regexp"
 shared "clashmanager/configyaml"
 "gopkg.in/yaml.v3"
)

var editableSections = map[string]string{"proxies":"proxies","groups":"proxy-groups","rules":"rules","rule-providers":"rule-providers","dns":"dns","hosts":"hosts","network":"","geo":""}
var networkKeys=[]string{"mixed-port","port","socks-port","allow-lan","bind-address","authentication","skip-auth-prefixes"}
var geoKeys=[]string{"geodata-mode","geox-url","geo-auto-update","geo-update-interval"}
func sectionDocument(r request)(any,error){
 field,ok:=editableSections[r.Section];if !ok{return nil,fmt.Errorf("不支持的配置区段")}
 enhanced,err:=shared.ApplyProfileExtensionChain([]byte(r.YAML),r.Name,r.Global,r.Extensions);if err!=nil{return nil,err}
 var doc map[string]any;if err=yaml.Unmarshal(enhanced,&doc);err!=nil{return nil,err}
 value:=doc[field]
 if field=="" { value=map[string]any{};keys:=networkKeys;if r.Section=="geo"{keys=geoKeys};for _,k:=range keys{if v,exists:=doc[k];exists{value.(map[string]any)[k]=v}} }
 if value==nil {if r.Section=="proxies"||r.Section=="groups"||r.Section=="rules"{value=[]any{}}else{value=map[string]any{}}}
 if r.Action!="save"{ b,e:=yaml.Marshal(value);return string(b),e }
 var replacement any
 if err=yaml.Unmarshal([]byte(r.Value),&replacement);err!=nil{return nil,err}
 extensions:=map[string]string{};for k,v:=range r.Extensions{extensions[k]=v}
 if r.Section=="proxies"||r.Section=="groups"||r.Section=="rules" {
  list,ok:=replacement.([]any);if !ok{return nil,fmt.Errorf("需要 YAML 列表，以 - 开始")}
  var original map[string]any;_ = yaml.Unmarshal([]byte(r.YAML),&original)
  excluded:=[]string{};if source,ok:=original[field].([]any);ok{for _,v:=range source{if r.Section=="rules"{excluded=append(excluded,fmt.Sprint(v))}else if obj,ok:=v.(map[string]any);ok{excluded=append(excluded,fmt.Sprint(obj["name"]))}}}
  extension:=map[string]any{"prepend":list,"append":[]any{},"delete":excluded};b,e:=yaml.Marshal(extension);if e!=nil{return nil,e};extensions[r.Section]=string(b)
 } else {
  object,ok:=replacement.(map[string]any);if !ok{return nil,fmt.Errorf("需要 YAML 对象")}
  patch:=map[string]any{};if strings.TrimSpace(extensions["override"])!=""{if err=yaml.Unmarshal([]byte(extensions["override"]),&patch);err!=nil{return nil,err}}
  if field!="" {patch[field]=object} else {keys:=networkKeys;if r.Section=="geo"{keys=geoKeys};for k:=range object{found:=false;for _,allowed:=range keys{if k==allowed{found=true}};if !found{return nil,fmt.Errorf("此页不支持字段 %s",k)}};for k,v:=range object{patch[k]=v}}
  b,e:=yaml.Marshal(patch);if e!=nil{return nil,e};extensions["override"]=string(b)
 }
 return extensions,nil
}

var secretPattern=regexp.MustCompile(`(?i)(authorization|password|token|secret|uuid|private-key|subscription-userinfo)[=: ]+[^ ,;]+`)
var urlPattern=regexp.MustCompile(`(?i)https?://[^\s]+`)
func redactLog(s string)string {
 s=urlPattern.ReplaceAllString(s,"[URL]")
 s=secretPattern.ReplaceAllString(s,"$1=[redacted]")
 if len(s)>1500{s=s[:1500]+"…"};return s
}
