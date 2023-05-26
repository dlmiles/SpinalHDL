package spinal.core.formal;

import java.util.ArrayList;
import java.util.List;

public class FormalResultException extends Exception {

    static final long serialVersionUID = 108679967467236937L;

    private boolean hasResult;
    private Integer exitStatus;
    private boolean isFail;
    private boolean isPass;
    private Integer resultCode;

    public FormalResultException() {
    }

    public FormalResultException(String message) {
        super(message);
    }

    public FormalResultException(String message, Throwable cause) {
        super(message, cause);
    }

    public FormalResultException(Throwable cause) {
        super(cause);
    }

    public FormalResultException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public boolean hasResult() {
        return hasResult;
    }
    public int exitStatus() {
        return exitStatus;
    }
    public boolean isFail() {
        return isFail;
    }
    public boolean isPass() {
        return isPass;
    }
    public Integer getResultCode() {
        return resultCode;
    }

    public String toString() {
        List<String> list = new ArrayList<>();
        if(hasResult)
            list.add("COMPLETED");
        if(isPass)
            list.add("verdict=PASS");
        if(isFail)
            list.add("verdict=FAIL");
        if(exitStatus != null && exitStatus != 0)
            list.add("exitStatus=" + exitStatus);
        if(resultCode != null)
            list.add("rc=" + resultCode);
        list.add("message=\"" + getMessage() + "\"");
        return String.join(", ", list);
    }

    public String toStringPretty() {
        return FormalResultException.class.getSimpleName() + "(" + toString() + ")";
    }

    public static FormalResultException builder(String message, boolean hasResult, Integer exitStatus, Boolean passOrFail, Integer resultCode) {
        FormalResultException exc = new FormalResultException(message);
        exc.hasResult = hasResult;
        exc.exitStatus = exitStatus;
        if(Boolean.TRUE.equals(passOrFail))
            exc.isPass = true;
        if(Boolean.FALSE.equals(passOrFail))
            exc.isFail = true;
        exc.resultCode = resultCode;
        return exc;
    }
}
