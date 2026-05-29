package xyz.wismer.nativestart.config;

import java.io.File;

public class Locations {
    /** The location of the files in the source code repository */
    private File source;
    /** The location of the files for distributions (e.g. an URL) */
    private String distribution;
    /** The location of the installed files relative to the application installation folder */
    private String target;

    public File getSource() {
        return source;
    }

    public void setSource(File source) {
        this.source = source;
    }

    public String getDistribution() {
        return distribution;
    }

    public void setDistribution(String distribution) {
        this.distribution = distribution;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
