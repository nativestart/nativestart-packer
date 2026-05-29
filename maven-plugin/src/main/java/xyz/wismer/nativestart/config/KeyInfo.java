package xyz.wismer.nativestart.config;

import java.io.File;

public class KeyInfo {
    /** The p12 keystore file */
    private File keystore;
    /** The alias of the entry containing the key */
    private String alias;
    /** The password for the keystore */
    private String storepass;
    /** The password for the key (if it is different from the one for the keystore) */
    private String keypass;

    public File getKeystore() {
        return keystore;
    }

    public void setKeystore(File keystore) {
        this.keystore = keystore;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getStorepass() {
        return storepass;
    }

    public void setStorepass(String storepass) {
        this.storepass = storepass;
    }

    public String getKeypass() {
        return keypass;
    }

    public void setKeypass(String keypass) {
        this.keypass = keypass;
    }
}
