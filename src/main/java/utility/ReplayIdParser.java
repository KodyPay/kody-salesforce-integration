package utility;

import com.google.protobuf.ByteString;

public class ReplayIdParser {
    /**
     * NOTE: replayIds are meant to be opaque (See docs: https://developer.salesforce.com/docs/platform/pub-sub-api/guide/intro.html)
     * and this is used for example purposes only. A long-lived subscription client will use the stored replay to
     * resubscribe on failure. The stored replay should be in bytes and not in any other form.
     */
    public static ByteString getByteStringFromReplayIdString(String input) {
        ByteString replayId;
        String[] values = input.substring(1, input.length()-2).split(",");
        byte[] b = new byte[values.length];
        int i=0;
        for (String x : values) {
            if (x.strip().length() != 0) {
                b[i++] = (byte)Integer.parseInt(x.strip());
            }
        }
        replayId = ByteString.copyFrom(b);
        return replayId;
    }

    /**
     * 将 ByteString 类型的 replayId 转换回原来的输入字符串格式。
     *
     * @param replayId 要转换的 ByteString 类型的 replayId
     * @return 转换后的字符串，格式类似于 "[1, 2, 3]"
     */
    public static String getReplayIdStringFromByteString(ByteString replayId) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < replayId.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(replayId.byteAt(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
