package main

/*
int ClashProtectSocket(int fd);
*/
import "C"

import (
    "fmt"
    "syscall"
    "github.com/metacubex/mihomo/component/dialer"
)

func init() {
    // Install once: TCP connects and UDP listeners must escape the TUN via
    // VpnService.protect, including in Android always-on lockdown mode.
    dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) error {
        protected := false
        if err := conn.Control(func(fd uintptr) { protected = C.ClashProtectSocket(C.int(fd)) != 0 }); err != nil { return err }
        if !protected { return fmt.Errorf("Android VPN socket protection failed") }
        return nil
    }
}
