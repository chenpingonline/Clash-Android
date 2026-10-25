package main

import (
 "bytes"
 "net/http"
 "net/http/httptest"
 "os"
 "sync/atomic"
 "testing"
 "github.com/metacubex/mihomo/component/geodata"
 "github.com/metacubex/mihomo/component/geodata/router"
 "github.com/metacubex/mihomo/component/updater"
 C "github.com/metacubex/mihomo/constant"
 "google.golang.org/protobuf/proto"
)

func TestGeoDownloadsValidateAndKeepOldDataOnFailure(t *testing.T) {
 oldHome:=C.Path.HomeDir();C.SetHomeDir(t.TempDir());defer C.SetHomeDir(oldHome)
 oldIP,oldSite:=geodata.GeoIpUrl(),geodata.GeoSiteUrl();defer geodata.SetGeoIpUrl(oldIP);defer geodata.SetGeoSiteUrl(oldSite)
 ip,_:=proto.Marshal(&router.GeoIPList{Entry:[]*router.GeoIP{{CountryCode:"cn",Cidr:[]*router.CIDR{{Ip:[]byte{203,0,113,0},Prefix:24}}}}})
 site,_:=proto.Marshal(&router.GeoSiteList{Entry:[]*router.GeoSite{{CountryCode:"cn",Domain:[]*router.Domain{{Value:"fixture.test"}}}}})
 var invalid atomic.Bool
 server:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){if invalid.Load(){_,_=w.Write([]byte("corrupt geo data"));return};if r.URL.Path=="/ip"{_,_=w.Write(ip)}else{_,_=w.Write(site)}}));defer server.Close()
 geodata.SetGeoIpUrl(server.URL+"/ip");geodata.SetGeoSiteUrl(server.URL+"/site")
 if e:=updater.UpdateGeoIp();e!=nil{t.Fatal(e)};if e:=updater.UpdateGeoSite();e!=nil{t.Fatal(e)}
 invalid.Store(true)
 if updater.UpdateGeoIp()==nil || updater.UpdateGeoSite()==nil{t.Fatal("invalid GEO was accepted")}
 for path,want:=range map[string][]byte{C.Path.GeoIP():ip,C.Path.GeoSite():site}{got,e:=os.ReadFile(path);if e!=nil||!bytes.Equal(got,want){t.Fatal("failed update replaced usable data",path,e)}}
}
