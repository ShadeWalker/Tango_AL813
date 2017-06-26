package com.mediatek.datashaping;

/** {@hide} */
interface IDataShapingManager
{
    void enableDataShaping();
    void disableDataShaping();
    boolean openLteDataUpLinkGate(boolean isForce);
}