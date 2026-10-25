package main

import (
 "fmt"
 "net"
 "strconv"
 "strings"
 "gopkg.in/yaml.v3"
)
func checkConfiguration(raw []byte,checkSockets bool)error{
 var doc map[string]any;if err:=yaml.Unmarshal(raw,&doc);err!=nil{return err}
 if rules,ok:=doc["rules"].([]any);ok{for i,rule:=range rules{if strings.HasPrefix(strings.TrimSpace(fmt.Sprint(rule)),"MATCH,")&&i!=len(rules)-1{return fmt.Errorf("MATCH 必须位于最后一条规则，后续规则无法匹配（第 %d 条）",i+1)}}}
 used:=map[int]string{19097:"本机控制器"}
 for _,key:=range []string{"port","socks-port","mixed-port"}{
  value,exists:=doc[key];if !exists{continue};port,err:=strconv.Atoi(fmt.Sprint(value));if err!=nil||port<0||port>65535{return fmt.Errorf("%s 端口需为0–65535",key)};if port==0{continue}
  if other,ok:=used[port];ok{return fmt.Errorf("%s 与 %s 端口冲突",key,other)};used[port]=key
  if checkSockets {
   host:="127.0.0.1";if doc["allow-lan"]==true{if h,ok:=doc["bind-address"].(string);ok{host=h};if host=="*"{host=""}}
   address:=net.JoinHostPort(host,strconv.Itoa(port));listener,err:=net.Listen("tcp",address);if err!=nil{return fmt.Errorf("%s TCP监听失败，请检查端口占用或地址",key)};listener.Close()
   if key!="port"{listener,err:=net.ListenPacket("udp",address);if err!=nil{return fmt.Errorf("%s UDP监听失败，请检查端口占用或地址",key)};listener.Close()}
  }
 }
 return nil
}
