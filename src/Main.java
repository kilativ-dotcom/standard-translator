import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    private static final String FILE_POSTFIX = "";
    private static final String TRANSLATIONS_PATH = "translations" + File.separator;

    private static final String HEADER_GROUP = "headerName";
    private static final String INDENT_GROUP = "indent";
    private static final String HEADER_REGEX = "(?<" + INDENT_GROUP + ">[\\t\\f ]*)\\\\scnheader\\{(?<" + HEADER_GROUP + ">(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{(?:[^{}]*|\\{[^{}]*\\})*?\\})*?\\})*?\\})*?\\})*?\\})*?\\})*?\\})*?)\\}";
    private static final String CSV_SEPARATION_REGEX = ",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)";
    private static final String ROLE_SIGN_REGEX = "\\\\scnrolesign";

    private static final Pattern HEADER_PATTERN = Pattern.compile(HEADER_REGEX);

    public static void main(String[] args) {
        System.out.println("Translating at paths:");
        Arrays.stream(args).forEach(System.out::println);
        Map<String, String> translations = readTranslations();
        Map<String, Set<String>> translated = new HashMap<>();
        Map<String, Set<String>> translationNotFound = new HashMap<>();

        List<File> texFiles = getFilesWithType(args, "\\btex\\b");
        System.out.println("found " + texFiles.size() + " tex files");
        appendTranslations(readFiles(texFiles), translations, translated, translationNotFound);
        System.out.println("translated " + translated.size() + " headers");
        System.out.println("couldn't translate " + translationNotFound.size() + " headers");
        for (Map.Entry<String, Set<String>> entry : translated.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("\nfound multiple headers `" + entry.getKey() + "` at files");
                entry.getValue().forEach(System.out::println);
            }
        }
        if (translated.size() < translations.size()) {
            System.out.println("\n\n=====================================================================================");
            System.out.println("not used " + (translations.size() - translated.size()) + " translations");
            StringBuilder sb = new StringBuilder();
            for (String russianHeader : translations.keySet()) {
                if (!translated.containsKey(russianHeader)) {
                    sb.append(String.format("\\scnheader{%s}%n\\scnidtf{%s}%n%n", russianHeader, translations.get(russianHeader)));
                }
            }
            File dictionary = new File(TRANSLATIONS_PATH + "dictionary.tex");
            writeToFile(dictionary.getAbsolutePath(), sb.toString().trim());
            System.out.println("not used translations have been written to " + dictionary.getAbsolutePath());
        }
    }

    private static Map<String, String> readTranslations() {
        Map<String, String> translationPairs = new HashMap<>();
        List<File> translations = getFilesWithType(new String[]{TRANSLATIONS_PATH}, "csv");
        System.out.println("Translation filess:");
        translations.forEach(System.out::println);
        System.out.println("--------------------------------");
        translations.forEach(file ->{
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.replaceAll("’", "'");
                    String[] values = line.split(CSV_SEPARATION_REGEX);
                    if (values.length != 2) {
                        System.err.println("File " + file + " has " + values.length + " entries at " + line);
                    } else {
                        if (translationPairs.containsKey(values[0])) {
                            System.err.println("Multiple translations found for " + values[0]);
                        } else {
                            if (values[0].startsWith("\"") && values[0].endsWith("\"")) {
                                values[0] = values[0].substring(1, values[0].length()-1);
                            }
                            if (values[1].startsWith("\"") && values[1].endsWith("\"")) {
                                values[1] = values[1].substring(1, values[1].length()-1);
                            }
                            translationPairs.put(values[0], values[1]);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        System.out.println("found " + translationPairs.size() + " translation pairs");
        return translationPairs;
    }

    private static List<File> getFilesWithType(String[] args, String requiredType) {
        return Arrays.stream(args)
                .map(File::new)
                .filter(File::exists)
                .map(File::toPath)
                .flatMap(path -> {
                    try {
                        return Files.walk(path);
                    } catch (IOException e) {
                        System.err.println("ERROR: cannot get files for directory " + path);
                        e.printStackTrace();
                        return Arrays.stream(new Path[]{new File("").toPath()});
                    }
                })
                .filter(path -> !path.getFileName().toFile().getName().isEmpty())
                .map(Path::toFile)
                .filter(File::isFile)
                .filter(File::canRead)
                .filter(file -> {
                    try {
                        String type = Files.probeContentType(file.toPath());
                        return type != null && type.matches(".*?" + requiredType + ".*?");
                    } catch (IOException e) {
                        System.err.println("ERROR: cannot check type of " + file);
                        return false;
                    }
                })
                .map(File::getAbsoluteFile)
                .collect(Collectors.toList());
    }

    private static List<Pair<File, String>> readFiles(List<File> cppFiles) {
        return cppFiles.stream()
                .filter(File::exists)
                .map(Main::readLines)
                .filter(Main::fileIsNotEmpty)
                .collect(Collectors.toList());
    }

    private static Pair<File, String> readLines(File file) {
        try {
            return new Pair<>(file, String.join("\n", Files.readAllLines(file.toPath())));
        } catch (IOException e) {
            System.err.println("ERROR: cannot read " + file);
            return new Pair<>(new File(""), "");
        }
    }

    private static boolean fileIsNotEmpty(Pair<File, String> pair) {
        return !(pair.getFirst().getName().isEmpty() && pair.getSecond().isEmpty());
    }

    private static void appendTranslations(
            List<Pair<File, String>> files,
            Map<String, String> translations,
            Map<String, Set<String>> translated,
            Map<String, Set<String>> translationNotFound) {
        files.stream()
                .filter(pair -> HEADER_PATTERN.matcher(pair.getSecond()).find())
                .map(pair -> {
                    String content = pair.getSecond();
                    Matcher headerMatcher = HEADER_PATTERN.matcher(content);
                    StringBuffer sb = new StringBuffer();
                    int replacementsInFile = 0;
                    while (headerMatcher.find()) {
                        String group = headerMatcher.group();
                        String header = headerMatcher.group(HEADER_GROUP);
                        if (!translations.containsKey(header) && header.contains(ROLE_SIGN_REGEX)) {
                            header = header.replaceAll(ROLE_SIGN_REGEX, "'");
                        }
                        if (translations.containsKey(header)) {
                            if (translated.containsKey(header)) {
                                addPairToMultimap(header, pair.getFirst().toString(), translated);
                                headerMatcher.appendReplacement(sb, Matcher.quoteReplacement(group));
                                continue;
                            }
                            replacementsInFile++;
                            addPairToMultimap(header, pair.getFirst().toString(), translated);
                            String translation = translations.get(header);
                            String englishIdtf = String.format("\\scnidtf{%s}", translation);
                            if (content.contains(englishIdtf)) {
                                continue;
                            }
                            headerMatcher.appendReplacement(sb, Matcher.quoteReplacement(String.format("%s%n%s%s", group, headerMatcher.group(INDENT_GROUP), englishIdtf)));
                        } else {
                            headerMatcher.appendReplacement(sb, Matcher.quoteReplacement(group));
                            addPairToMultimap(header, pair.getFirst() + ":" + (sb.toString().split("\n", -1).length - replacementsInFile), translationNotFound);
                        }
                    }
                    headerMatcher.appendTail(sb);
                    if (replacementsInFile == 0) {
                        return new Pair<>(new File(""), "");
                    } else {
                        return new Pair<>(pair.getFirst(), sb.toString());
                    }
                })
                .filter(Main::fileIsNotEmpty)
                .peek(pair -> System.out.println("Translating scnheaders in " + pair.getFirst()))
                .forEach(pair -> writeToFile(pair.getFirst() + FILE_POSTFIX, pair.getSecond()));
    }

    private static void addPairToMultimap(String key, String value, Map<String, Set<String>> multimap) {
        Set<String> values = multimap.putIfAbsent(key, new LinkedHashSet<>());
        if (values == null) {
            values = multimap.get(key);
        }
        values.add(value);
    }

    private static void writeToFile(String filename, String content) {
        try (PrintWriter out = new PrintWriter(filename)) {
            out.println(content);
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: cannot write to a file " + filename);
        }
    }
}