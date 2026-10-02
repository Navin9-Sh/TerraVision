package ai.terravision.emailcheck;

import java.util.regex.Pattern;

public final class EmailSyntax {

    private static final Pattern LOCAL_PART =
            Pattern.compile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$");
    private static final Pattern LABEL = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");
    private static final Pattern TLD = Pattern.compile("^([A-Za-z]{2,63}|xn--[A-Za-z0-9-]{1,59})$");

    private EmailSyntax() {
    }

    public static boolean isValid(String email) {
        if (email == null || email.length() > 254) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 1 || at != email.indexOf('@')) {
            return false;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.length() > 64 || !LOCAL_PART.matcher(local).matches()) {
            return false;
        }
        return isValidDomain(domain);
    }

    public static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1).toLowerCase();
    }

    private static boolean isValidDomain(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return TLD.matcher(labels[labels.length - 1]).matches();
    }
}
