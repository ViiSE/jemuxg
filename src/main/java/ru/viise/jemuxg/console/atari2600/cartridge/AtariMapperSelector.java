package ru.viise.jemuxg.console.atari2600.cartridge;

import java.util.Locale;
import ru.viise.jemuxg.api.storage.Media;

/**
 * Выбор схемы картриджа Atari 2600.
 *
 * <p>По умолчанию схема определяется по размеру ROM. Для 8 КБ это
 * неоднозначно: по умолчанию выбирается F8, а E0/FE/F8SC можно задать
 * явно или подсказать именем файла ({@code e0}, {@code fe}, {@code f8sc}).
 * Схемы 3F/3E не определяются автоматически — их нужно указывать явно.</p>
 */
public final class AtariMapperSelector {

    public static final String OPTION = "mapper";

    private static final int TWO_KB = 2 * 1024;
    private static final int FOUR_KB = 4 * 1024;
    private static final int EIGHT_KB = 8 * 1024;
    private static final int DPC_10K = 10 * 1024;
    private static final int DPC_PADDED = 10_495;
    private static final int TWELVE_KB = 12 * 1024;
    private static final int SIXTEEN_KB = 16 * 1024;
    private static final int THIRTY_TWO_KB = 32 * 1024;

    private AtariMapperSelector() {
    }

    /**
     * Создаёт маппер по явному имени или автоматически.
     *
     * @param media     ROM картриджа
     * @param requested имя схемы ({@code auto}, {@code f8}, {@code e0}...)
     */
    public static AtariMapper select(Media media, String requested) {
        String scheme = requested == null || requested.isBlank()
                ? "auto"
                : requested.trim().toLowerCase(Locale.ROOT);
        int size = romSize(media);

        if ("auto".equals(scheme)) {
            return auto(media, size);
        }

        return switch (scheme) {
            case "2k" -> new AtariFixedMapper(requireSize(size, TWO_KB, "2K"));
            case "4k" -> new AtariFixedMapper(requireSize(size, FOUR_KB, "4K"));
            case "f8" -> {
                requireSize(size, EIGHT_KB, "F8");
                yield new AtariF8Mapper();
            }
            case "f6" -> {
                requireSize(size, SIXTEEN_KB, "F6");
                yield new AtariF6Mapper();
            }
            case "f4" -> {
                requireSize(size, THIRTY_TWO_KB, "F4");
                yield new AtariF4Mapper();
            }
            case "f8sc" -> {
                requireSize(size, EIGHT_KB, "F8SC");
                yield new AtariSuperChipMapper(new AtariF8Mapper());
            }
            case "f6sc" -> {
                requireSize(size, SIXTEEN_KB, "F6SC");
                yield new AtariSuperChipMapper(new AtariF6Mapper());
            }
            case "f4sc" -> {
                requireSize(size, THIRTY_TWO_KB, "F4SC");
                yield new AtariSuperChipMapper(new AtariF4Mapper());
            }
            case "3f" -> new Atari3FMapper(requireBankedSize(size, "3F"));
            case "3e" -> new Atari3EMapper(requireBankedSize(size, "3E"));
            case "e0" -> {
                requireSize(size, EIGHT_KB, "E0");
                yield new AtariE0Mapper(size);
            }
            case "e7" -> new AtariE7Mapper(requireE7Size(size));
            case "fe" -> {
                requireSize(size, EIGHT_KB, "FE");
                yield new AtariFeMapper(size);
            }
            case "dpc" -> new AtariDpcMapper(media);
            default -> throw new IllegalArgumentException(
                    "Unknown Atari 2600 mapper '" + requested
                            + "'. Supported: auto, 2k, 4k, f8, f6, f4, f8sc, f6sc, f4sc,"
                            + " 3f, 3e, e0, e7, fe, dpc");
        };
    }

    private static AtariMapper auto(Media media, int size) {
        String name = media.name().toLowerCase(Locale.ROOT);
        return switch (size) {
            case TWO_KB -> new AtariFixedMapper(TWO_KB);
            case FOUR_KB -> new AtariFixedMapper(FOUR_KB);
            case DPC_10K, DPC_PADDED -> new AtariDpcMapper(media);
            case EIGHT_KB -> {
                if (hasHint(name, "e7")) {
                    yield new AtariE7Mapper(size);
                }
                if (hasHint(name, "e0")) {
                    yield new AtariE0Mapper(size);
                }
                if (hasHint(name, "fe")) {
                    yield new AtariFeMapper(size);
                }
                if (hasHint(name, "f8sc")) {
                    yield new AtariSuperChipMapper(new AtariF8Mapper());
                }
                yield new AtariF8Mapper();
            }
            case TWELVE_KB -> new AtariE7Mapper(size);
            case SIXTEEN_KB -> {
                if (hasHint(name, "e7")) {
                    yield new AtariE7Mapper(size);
                }
                if (hasHint(name, "f6sc")) {
                    yield new AtariSuperChipMapper(new AtariF6Mapper());
                }
                yield new AtariF6Mapper();
            }
            case THIRTY_TWO_KB -> {
                if (hasHint(name, "f4sc")) {
                    yield new AtariSuperChipMapper(new AtariF4Mapper());
                }
                yield new AtariF4Mapper();
            }
            default -> throw new IllegalArgumentException(
                    "Cannot auto-detect Atari 2600 mapper for " + size
                            + " bytes (" + media.name() + "). Use --mapper <scheme>;"
                            + " schemes: 3f, 3e, e0, e7, fe, dpc, f8sc, f6sc, f4sc");
        };
    }

    /**
     * Ищет подсказку схемы как отдельный токен, а не подстроку:
     * {@code Defender} не должен включать FE, а {@code maze_e0} — E0.
     */
    private static boolean hasHint(String name, String token) {
        int index = name.indexOf(token);
        while (index >= 0) {
            int end = index + token.length();
            boolean leftFree = index == 0 || !Character.isLetterOrDigit(name.charAt(index - 1));
            boolean rightFree = end == name.length() || !Character.isLetterOrDigit(name.charAt(end));
            if (leftFree && rightFree) {
                return true;
            }
            index = name.indexOf(token, index + 1);
        }
        return false;
    }

    private static int romSize(Media media) {
        long size = media.size();
        if (size <= 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid Atari 2600 ROM size: " + size);
        }
        return (int) size;
    }

    private static int requireSize(int size, int expected, String scheme) {
        if (size != expected) {
            throw new IllegalArgumentException(
                    "Mapper " + scheme + " requires " + expected + " bytes, got " + size);
        }
        return size;
    }

    private static int requireE7Size(int size) {
        if (size != EIGHT_KB && size != TWELVE_KB && size != SIXTEEN_KB) {
            throw new IllegalArgumentException(
                    "Mapper E7 requires 8K, 12K or 16K, got " + size + " bytes");
        }
        return size;
    }

    private static int requireBankedSize(int size, String scheme) {
        if (size <= 0 || size % TWO_KB != 0) {
            throw new IllegalArgumentException(
                    "Mapper " + scheme + " requires a non-zero multiple of 2K, got "
                            + size + " bytes");
        }
        return size;
    }
}
