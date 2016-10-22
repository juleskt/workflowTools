import java.util.Collections;
import static com.vmware.scm.FileChangeType.copied;
    private String perforceChangelistId;
    private boolean unresolved;
    public FileChange(ScmType scmType, FileChangeType changeType, String... filesAffected) {
        this(scmType, null, changeType, filesAffected);
    }

    public void addFileAffected(String name) {
        this.filesAffected.add(name);
    }

    public String getPerforceChangelistId() {
        return perforceChangelistId;
    }

    public void setPerforceChangelistId(String perforceChangelistId) {
        this.perforceChangelistId = perforceChangelistId;
    }

    public void setUnresolved(boolean unresolved) {
        this.unresolved = unresolved;
    }

    public boolean isUnresolved() {
        return unresolved;
    }

    public boolean matchesOneOf(FileChangeType... changeTypes) {
        for (FileChangeType changeType : changeTypes) {
            if (this.changeType == changeType) {
                return true;
            }
        }
        return false;
    }

            case "kxtext":
            case "xbinary":
            case "ctext":
            case "ltext":
            case "uxbinary":
            case "xtempobj":
            case "xunicode":
            case "xutf16":
            case "ktext":
            case "binary":
            case "cxtext":
            case "xltext":
            case "tempobj":
    public void parseValue(String valueName, String value, String clientDirectoryToStrip) {
                String strippedPath = value.substring(clientDirectoryToStrip.length() + 1);
                addFileAffected(strippedPath);
                break;
            case "change":
                setPerforceChangelistId(value);
            case "haveRev":
            case "unresolved":
                setUnresolved(true);
                break;
    public String  diffGitLine() {
                    throw new RuntimeException("Expected to find file mode for new file " + bFile);
                }
        List<String> filesFromChangeToCheck = filesAffected;
        List<String> filesFromOtherChangeToCheck = that.filesAffected;
            } else if (that.changeType == copied) {
                changeTypeToUseForComparision = added;
                filesFromOtherChangeToCheck = Collections.singletonList(that.getLastFileAffected());
            } else if (changeType == copied && that.changeType == added) {
                changeTypeToUseForComparision = copied;
                filesFromChangeToCheck = Collections.singletonList(getLastFileAffected());
        return changeType == changeTypeToUseForComparision && Objects.deepEquals(filesFromChangeToCheck, filesFromOtherChangeToCheck);