package main

/*
#include <stdlib.h>
int ClashConnectionOwnerUid(int protocol, const char* source, int sourcePort,
    const char* destination, int destinationPort);
*/
import "C"

import (
	"unsafe"

	"github.com/metacubex/mihomo/component/process"
	M "github.com/metacubex/mihomo/constant"
)

func init() {
	process.DefaultConnectionOwnerResolver = func(metadata *M.Metadata) {
		// A local explicit proxy represents its proxy client, not necessarily the
		// original app. Only attribute traffic observed through our VPN tunnel.
		if metadata.Type != M.TUN || !metadata.SrcIP.IsValid() || !metadata.DstIP.IsValid() {
			return
		}
		protocol := 0
		switch metadata.NetWork {
		case M.TCP:
			protocol = 6
		case M.UDP:
			protocol = 17
		default:
			return
		}
		source := C.CString(metadata.SrcIP.String())
		destination := C.CString(metadata.DstIP.String())
		defer C.free(unsafe.Pointer(source))
		defer C.free(unsafe.Pointer(destination))
		uid := C.ClashConnectionOwnerUid(C.int(protocol), source, C.int(metadata.SrcPort), destination, C.int(metadata.DstPort))
		if uid > 0 {
			metadata.Uid = uint32(uid)
		}
	}
}
