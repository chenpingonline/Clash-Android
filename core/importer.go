package main

import (
 "crypto/sha256"
 "encoding/base64"
 "fmt"
 "io"
 "net/http"
 "net/url"
 "os"
 "path/filepath"
 "time"
 ri "clashmanager/ruleimport"
 MC "github.com/metacubex/mihomo/constant"
)
func fetchRules(address string)([]byte,error){
 u,e:=url.Parse(address);if e!=nil||u.Scheme!="https"||u.User!=nil{return nil,fmt.Errorf("规则来源仅允许 HTTPS")}
 client:=http.Client{Timeout:20*time.Second,CheckRedirect:func(*http.Request,[]*http.Request)error{return http.ErrUseLastResponse}}
 response,e:=client.Get(address);if e!=nil{return nil,fmt.Errorf("无法下载规则来源")};defer response.Body.Close()
 if response.StatusCode!=200{return nil,fmt.Errorf("规则下载 HTTP %d",response.StatusCode)}
 b,e:=io.ReadAll(io.LimitReader(response.Body,ri.MaxInput+1));if len(b)>ri.MaxInput{return nil,fmt.Errorf("规则内容超过8 MiB")};return b,e
}
func importRules(r request)(any,error){
 raw:=[]byte(r.Value);var err error
 if r.Data!=""{raw,err=base64.StdEncoding.DecodeString(r.Data);if err!=nil{return nil,err}}
 if r.Options.URL!=""{raw,err=fetchRules(r.Options.URL);if err!=nil{return nil,err}}
 parsed,err:=ri.Parse(raw,r.Options,fetchRules);if err!=nil{return nil,err}
 plan,err:=ri.Build(parsed,r.Options,[]byte(r.YAML),r.Name,r.Global,r.Extensions,func(b []byte)string{return fmt.Sprintf("rules/import-%x.mrs",sha256.Sum256(b))});if err!=nil{return nil,err}
 if len(plan.Errors)==0 {
  for path,bytes:=range plan.Assets{target:=filepath.Join(MC.Path.HomeDir(),path);if err=os.MkdirAll(filepath.Dir(target),0700);err!=nil{return nil,err};if err=os.WriteFile(target,bytes,0600);err!=nil{return nil,err}}
 }
 // Asset writes are content-addressed and inert until validated extensions are saved.
 next:=map[string]string{};for k,v:=range r.Extensions{next[k]=v};for k,v:=range plan.Extensions{next[k]=v};plan.Extensions=next
 return plan,nil
}
