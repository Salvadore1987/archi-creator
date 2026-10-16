package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.regex.Pattern;

/**
 * Имя ветки или ссылки по правилам Git: без пробелов, управляющих и служебных символов,
 * без {@code ..}, <code>&#64;&#123;</code>, {@code //}; ни имя, ни его компонент не начинается точкой,
 * не кончается на {@code .lock}; имя не начинается знаком {@code -} или {@code /}
 * и не кончается точкой или {@code /}. Ведущий {@code -} Git принял бы за ключ команды.
 */
final class GitNames {

    private static final Pattern NAME = Pattern.compile("^(?![/.-])(?!.*/\\.)(?!.*//)(?!.*\\.\\.)(?!.*@\\{)"
            + "(?!.*\\.lock(?:/|$))(?!.*/$)(?!.*\\.$)[^\\s~^:?*\\[\\\\\\x00-\\x1f\\x7f]+$");

    private GitNames() {
    }

    /** Годится ли строка именем ветки или ссылки. */
    static boolean valid(String name) {
        return NAME.matcher(name).matches();
    }
}
