ui_print "- Installing SView Overlay..."
ui_print "- Magisk & APatch: Reboot."
ui_print "- KernelSU: Reboot, And grant 'SView Overlay' SU."
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
