package main

import (
 "context"
 "encoding/json"
 "fmt"
 "io"
 "net"
 "net/http"
 "net/netip"
 "net/url"
 "strconv"
 "time"
 C "github.com/metacubex/mihomo/constant"
 "github.com/metacubex/mihomo/tunnel"
)

// Probe enters the routing engine explicitly, using the same rules as TUN traffic.
func connectivity(address string)(any,error){
 if !running{return nil,fmt.Errorf("请先启动VPN")}
 u,e:=url.Parse(address);if e!=nil||u.Scheme!="https"||u.Hostname()==""||u.User!=nil{return nil,fmt.Errorf("检测地址需要 HTTPS")}
 transport:=&http.Transport{DisableKeepAlives:true,DialContext:func(ctx context.Context,network,addr string)(net.Conn,error){
  host,port,e:=net.SplitHostPort(addr);if e!=nil{return nil,e};number,e:=strconv.ParseUint(port,10,16);if e!=nil{return nil,e}
  client,server:=net.Pipe()
  metadata:=&C.Metadata{NetWork:C.TCP,Type:C.INNER,Host:host,DstPort:uint16(number)}
  if ip,e:=netip.ParseAddr(host);e==nil{metadata.DstIP=ip;metadata.Host=""}
  go tunnel.Tunnel.HandleTCPConn(server,metadata)
  return client,nil
 }}
 defer transport.CloseIdleConnections()
 client:=http.Client{Transport:transport,Timeout:10*time.Second,CheckRedirect:func(*http.Request,[]*http.Request)error{return http.ErrUseLastResponse}}
 start:=time.Now();response,e:=client.Get(address);if e!=nil{return nil,fmt.Errorf("通过路由内核的连通检测失败")};defer response.Body.Close()
 result:=map[string]any{"status":response.StatusCode,"elapsedMs":time.Since(start).Milliseconds(),"exitIP":""}
 body,_:=io.ReadAll(io.LimitReader(response.Body,4096));var data struct{IP string `json:"ip"`};_ = json.Unmarshal(body,&data)
 if ip,e:=netip.ParseAddr(data.IP);e==nil{result["exitIP"]=ip.String()}
 return result,nil
}
