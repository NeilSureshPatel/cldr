package org.unicode.cldr.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.test.CheckCLDR.InputMethod;
import org.unicode.cldr.test.CheckCLDR.Phase;
import org.unicode.cldr.test.CheckCLDR.StatusAction;
import org.unicode.cldr.tool.FormattedFileWriter.Anchors;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.Annotations;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRInfo.CandidateInfo;
import org.unicode.cldr.util.CLDRInfo.PathValueInfo;
import org.unicode.cldr.util.CLDRInfo.UserInfo;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CLDRURLS;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.CoreCoverageInfo;
import org.unicode.cldr.util.CoreCoverageInfo.CoreItems;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Counter2;
import org.unicode.cldr.util.CoverageInfo;
import org.unicode.cldr.util.DtdType;
import org.unicode.cldr.util.LanguageTagCanonicalizer;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.Organization;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.Factory;
import org.unicode.cldr.util.PathHeader.SurveyToolStatus;
import org.unicode.cldr.util.PathStarrer;
import org.unicode.cldr.util.PatternCache;
import org.unicode.cldr.util.RegexLookup;
import org.unicode.cldr.util.RegexLookup.LookupType;
import org.unicode.cldr.util.SimpleFactory;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.VettingViewer.MissingStatus;
import org.unicode.cldr.util.VoteResolver.VoterInfo;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.impl.Relation;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;

public class ShowLocaleCoverage {
    private static final double BASIC_THRESHOLD = 1;
    private static final double MODERATE_THRESHOLD = 0.995;
    private static final double MODERN_THRESHOLD = 0.995;

    private static final String VXML_CONSTANT = CLDRPaths.AUX_DIRECTORY + "voting/" + CLDRFile.GEN_VERSION + "/vxml/common/";
    private static final CLDRConfig CONFIG = CLDRConfig.getInstance();
    private static final String TSV_MISSING_SUMMARY_HEADER =
        "#Path Level"
            + "\t#Locales"
            + "\tLocales"
            + "\tSection"
            + "\tPage"
            + "\tHeader"
            + "\tCode"
            ;
    private static final String TSV_LOCALE_COVERAGE_HEADER =
        "#Dir"
            + "\tCode"
            + "\tEnglish Name"
            + "\tNative Name"
            + "\tScript"
            + "\tCLDR Target"
            + "\tSublocales"
            + "\tFields\tUC\tMissing"
            + "\tModern\tMiss +UC"
            + "\tModerate\tMiss +UC"
            + "\tBasic\tMiss +UC"
            + "\tCore\tMiss +UC"
            + "\tCore-Missing";

    private static final String TSV_MISSING_HEADER =
        "#LCode"
            + "\tEnglish Name"
            + "\tScript"
            + "\tLocale Level"
            + "\tPath Level"
            + "\tSTStatus"
            + "\tBailey"
            + "\tSection"
            + "\tPage"
            + "\tHeader"
            + "\tCode"
            + "\tST Link"
            ;
    private static final String TSV_MISSING_BASIC_HEADER = "";

    private static final boolean DEBUG = true;
    private static final char DEBUG_FILTER = 0; // use letter to only load locales starting with that letter

    private static final String LATEST = ToolConstants.CHART_VERSION;
    private static final double CORE_SIZE = CoreItems.values().length - CoreItems.ONLY_RECOMMENDED.size();
    public static CLDRConfig testInfo = ToolConfig.getToolInstance();
    private static final StandardCodes SC = StandardCodes.make();
    private static final SupplementalDataInfo SUPPLEMENTAL_DATA_INFO = testInfo.getSupplementalDataInfo();
    private static final StandardCodes STANDARD_CODES = SC;

    static org.unicode.cldr.util.Factory factory = testInfo.getCommonAndSeedAndMainAndAnnotationsFactory();
    private static final CLDRFile ENGLISH = factory.make("en", true);

    private static UnicodeSet ENG_ANN = Annotations.getData("en").keySet();

    // added info using pattern in VettingViewer.

    static final RegexLookup<Boolean> HACK = RegexLookup.<Boolean> of(LookupType.STANDARD, RegexLookup.RegexFinderTransformPath)
        .add("//ldml/localeDisplayNames/keys/key[@type=\"(d0|em|fw|i0|k0|lw|m0|rg|s0|ss|t0|x0)\"]", true)
        .add("//ldml/localeDisplayNames/types/type[@key=\"(em|fw|kr|lw|ss)\"].*", true)
        .add("//ldml/localeDisplayNames/languages/language[@type=\".*_.*\"]", true)
        .add("//ldml/localeDisplayNames/languages/language[@type=\".*\"][@alt=\".*\"]", true)
        .add("//ldml/localeDisplayNames/territories/territory[@type=\".*\"][@alt=\".*\"]", true)
        .add("//ldml/localeDisplayNames/territories/territory[@type=\"EZ\"]", true);

    //private static final String OUT_DIRECTORY = CLDRPaths.GEN_DIRECTORY + "/coverage/"; // CldrUtility.MAIN_DIRECTORY;

    final static Options myOptions = new Options();

    enum MyOptions {
        filter(".+", ".*", "Filter the information based on id, using a regex argument."),
        //        draftStatus(".+", "unconfirmed", "Filter the information to a minimum draft status."),
        chart(null, null, "chart only"),
        growth("true", "true", "Compute growth data"),
        organization(".+", null, "Only locales for organization"),
        version(".+",
            LATEST, "To get different versions"),
        rawData(null, null, "Output the raw data from all coverage levels"),
        targetDir(".*",
            CLDRPaths.GEN_DIRECTORY + "/statistics/", "target output file."),
        directories("(.*:)?[a-z]+(,[a-z]+)*", "common",
            "Space-delimited list of main source directories: common,seed,exemplar.\n" +
            "Optional, <baseDir>:common,seed"),;

        // targetDirectory(".+", CldrUtility.CHART_DIRECTORY + "keyboards/", "The target directory."),
        // layouts(null, null, "Only create html files for keyboard layouts"),
        // repertoire(null, null, "Only create html files for repertoire"), ;
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    static final RegexLookup<Boolean> SUPPRESS_PATHS_CAN_BE_EMPTY = new RegexLookup<Boolean>()
        .add("\\[@alt=\"accounting\"]", true)
        .add("\\[@alt=\"variant\"]", true)
        .add("^//ldml/localeDisplayNames/territories/territory.*@alt=\"short", true)
        .add("^//ldml/localeDisplayNames/languages/language.*_", true)
        .add("^//ldml/numbers/currencies/currency.*/symbol", true)
        .add("^//ldml/characters/exemplarCharacters", true);

    static DraftStatus minimumDraftStatus = DraftStatus.unconfirmed;
    static final Factory pathHeaderFactory = PathHeader.getFactory(ENGLISH);

    static boolean RAW_DATA = true;
    private static Set<String> COMMON_LOCALES;

    public static void main(String[] args) throws IOException {
        myOptions.parse(MyOptions.filter, args, true);

        Matcher matcher = PatternCache.get(MyOptions.filter.option.getValue()).matcher("");

        if (MyOptions.chart.option.doesOccur()) {
            showCoverage(null, matcher);
            return;
        }


        if (MyOptions.growth.option.doesOccur()) {
            try (PrintWriter out = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-growth.tsv")) {
                doGrowth(matcher, out);
                return;
            }
        }

        Set<String> locales = null;
        String organization = MyOptions.organization.option.getValue();
        boolean useOrgLevel = MyOptions.organization.option.doesOccur();
        if (useOrgLevel) {
            locales = STANDARD_CODES.getLocaleCoverageLocales(organization);
        }

        if (MyOptions.version.option.doesOccur()) {
            String number = MyOptions.version.option.getValue().trim();
            if (!number.contains(".")) {
                number += ".0";
            }
            factory = org.unicode.cldr.util.Factory.make(
                CLDRPaths.ARCHIVE_DIRECTORY + "cldr-" + number + "/common/main/", ".*");
        } else {
            if (MyOptions.directories.option.doesOccur()) {
                String directories = MyOptions.directories.option.getValue().trim();
                CLDRConfig cldrConfig = CONFIG;
                String base = null;
                int colonPos = directories.indexOf(':');
                if (colonPos >= 0) {
                    base = directories.substring(0, colonPos).trim();
                    directories = directories.substring(colonPos + 1).trim();
                } else {
                    base = cldrConfig.getCldrBaseDirectory().toString();
                }
                String[] items = directories.split(",\\s*");
                File[] fullDirectories = new File[items.length];
                int i = 0;
                for (String item : items) {
                    fullDirectories[i++] = new File(base + "/" + item + "/main");
                }
                factory = SimpleFactory.make(fullDirectories, ".*");
                COMMON_LOCALES = SimpleFactory.make(base + "/" + "common" + "/main", ".*").getAvailableLanguages();
            }
        }
        fixCommonLocales();

        RAW_DATA = MyOptions.rawData.option.doesOccur();

        //showEnglish();

        showCoverage(null, matcher, locales, useOrgLevel);
    }

    public static void fixCommonLocales() {
        if (COMMON_LOCALES == null) {
            COMMON_LOCALES = factory.getAvailableLanguages();
        }
    }

    private static void doGrowth(Matcher matcher, PrintWriter out) {
        TreeMap<String, List<Double>> growthData = new TreeMap<>(Ordering.natural().reverse()); // sort by version, descending
//        if (DEBUG) {
//            for (String dir : new File(CLDRPaths.ARCHIVE_DIRECTORY).list()) {
//                if (!dir.startsWith("cldr")) {
//                    continue;
//                }
//                String version = getNormalizedVersion(dir);
//                if (version == null) {
//                    continue;
//                }
//                org.unicode.cldr.util.Factory newFactory = org.unicode.cldr.util.Factory.make(
//                    CLDRPaths.ARCHIVE_DIRECTORY + "/" + dir + "/common/main/", ".*");
//                System.out.println("Reading: " + version);
//                Map<String, FoundAndTotal> currentData = addGrowth(newFactory, matcher);
//                System.out.println("Read: " + version + "\t" + currentData);
//                break;
//            }
//        }
        Map<String, FoundAndTotal> latestData = null;
        for (ReleaseInfo versionNormalizedVersionAndYear : versionToYear) {
            VersionInfo version = versionNormalizedVersionAndYear.version;
            int year = versionNormalizedVersionAndYear.year;
            String dir = ToolConstants.getBaseDirectory(version.getVersionString(2, 3));
            Map<String, FoundAndTotal> currentData = addGrowth(factory, dir, matcher, false);
            System.out.println("year: " + year + "; version: " + version + "; size: " + currentData);
            out.flush();
            if (latestData == null) {
                latestData = currentData;
            }
            Counter2<String> completionData = getCompletion(latestData, currentData);
            addCompletionList(year+"", completionData, growthData);
            if (DEBUG) System.out.println(currentData);
        }
//        Map<String, FoundAndTotal> latestData = addGrowth(factory, null, matcher, false);
//        addCompletionList(getYearFromVersion(LATEST, false), getCompletion(latestData, latestData), growthData);
//        if (DEBUG) System.out.println(latestData);
//        //System.out.println(growthData);
//        List<String> dirs = new ArrayList<>(Arrays.asList(new File(CLDRPaths.ARCHIVE_DIRECTORY).list()));
//        Collections.reverse(dirs);
//        for (String dir : dirs) {
//            if (!dir.startsWith("cldr")) {
//                continue;
//            }
//            String version = getNormalizedVersion(dir);
//            if (version == null) {
//                continue;
//            }
////            if (version.compareTo("12") < 0) {
////                continue;
////            }
//            System.out.println("Reading: " + version);
//            if (version.equals("2008")) {
//                int debug = 0;
//            }
//            Map<String, FoundAndTotal> currentData = addGrowth(factory, dir, matcher, false);
//            System.out.println("Read: " + version + "\t" + currentData);
//            Counter2<String> completionData = getCompletion(latestData, currentData);
//            //System.out.println(version + "\t" + completionData);
//            addCompletionList(version, completionData, growthData);
//            if (DEBUG) System.out.println(currentData);
//        }
        boolean first = true;
        for (Entry<String, List<Double>> entry : growthData.entrySet()) {
            if (first) {
                for (int i = 0; i < entry.getValue().size(); ++i) {
                    out.print("\t" + i);
                }
                out.println();
                first = false;
            }
            out.println(entry.getKey() + "\t" + Joiner.on("\t").join(entry.getValue()));
        }
    }

    static final class ReleaseInfo {
        public ReleaseInfo(VersionInfo versionInfo, int year) {
            this.version = versionInfo;
            this.year = year;
        }
        VersionInfo version;
        int year;
    }

    // TODO merge this into ToolConstants, and have the version expressed as VersionInfo.
    static final List<ReleaseInfo> versionToYear;
    static {
        Object[][] mapping = {
            { VersionInfo.getInstance(40), 2021 },
            { VersionInfo.getInstance(38), 2020 },
            { VersionInfo.getInstance(36), 2019 },
            { VersionInfo.getInstance(34), 2018 },
            { VersionInfo.getInstance(32), 2017 },
            { VersionInfo.getInstance(30), 2016 },
            { VersionInfo.getInstance(28), 2015 },
            { VersionInfo.getInstance(26), 2014 },
            { VersionInfo.getInstance(24), 2013 },
            { VersionInfo.getInstance(22,1), 2012 },
            { VersionInfo.getInstance(2,0,1), 2011 },
            { VersionInfo.getInstance(1,9,1), 2010 },
            { VersionInfo.getInstance(1,7,2), 2009 },
            { VersionInfo.getInstance(1,6,1), 2008 },
            { VersionInfo.getInstance(1,5,1), 2007 },
            { VersionInfo.getInstance(1,4,1), 2006 },
            { VersionInfo.getInstance(1,3), 2005 },
            { VersionInfo.getInstance(1,2), 2004 },
            { VersionInfo.getInstance(1,1,1), 2003 },
        };
        List<ReleaseInfo> _versionToYear = new ArrayList<>();
        for (Object[] row : mapping) {
            _versionToYear.add(new ReleaseInfo((VersionInfo)row[0], (int)row[1]));
        }
        versionToYear = ImmutableList.copyOf(_versionToYear);
    }

//    public static String getNormalizedVersion(String dir) {
//        String rawVersion = dir.substring(dir.indexOf('-') + 1);
//        int firstDot = rawVersion.indexOf('.');
//        int secondDot = rawVersion.indexOf('.', firstDot + 1);
//        if (secondDot > 0) {
//            rawVersion = rawVersion.substring(0, firstDot) + rawVersion.substring(firstDot + 1, secondDot);
//        } else {
//            rawVersion = rawVersion.substring(0, firstDot);
//        }
//        String result = getYearFromVersion(rawVersion, true);
//        return result == null ? null : result.toString();
//    }

//    private static String getYearFromVersion(String version, boolean allowNull) {
//        String result = versionToYear.get(version);
//        if (!allowNull && result == null) {
//            throw new IllegalArgumentException("No year for version: " + version);
//        }
//        return result;
//    }
//
//    private static String getVersionFromYear(String year, boolean allowNull) {
//        String result = versionToYear.inverse().get(year);
//        if (!allowNull && result == null) {
//            throw new IllegalArgumentException("No version for year: " + year);
//        }
//        return result;
//    }

    public static void addCompletionList(String version, Counter2<String> completionData, TreeMap<String, List<Double>> growthData) {
        List<Double> x = new ArrayList<>();
        for (String key : completionData.getKeysetSortedByCount(false)) {
            x.add(completionData.getCount(key));
        }
        growthData.put(version, x);
        System.out.println(version + "\t" + x.size());
    }

    public static Counter2<String> getCompletion(Map<String, FoundAndTotal> latestData, Map<String, FoundAndTotal> currentData) {
        Counter2<String> completionData = new Counter2<>();
        for (Entry<String, FoundAndTotal> entry : latestData.entrySet()) {
            final String locale = entry.getKey();
            final FoundAndTotal currentRecord = currentData.get(locale);
            if (currentRecord == null) {
                continue;
            }
            double total = entry.getValue().total;
            if (total == 0) {
                continue;
            }
            double completion = currentRecord.found / total;
            completionData.add(locale, completion);
        }
        return completionData;
    }

    static class FoundAndTotal {
        final int found;
        final int total;

        public FoundAndTotal(Counter<Level>... counters) {
            final int[] count = { 0, 0, 0 };
            for (Level level : Level.values()) {
                if (level == Level.COMPREHENSIVE || level == Level.OPTIONAL) {
                    continue;
                }
                int i = 0;
                for (Counter<Level> counter : counters) {
                    count[i++] += counter.get(level);
                }
            }
            found = count[0];
            total = found + count[1] + count[2];
        }

        @Override
        public String toString() {
            return found + "/" + total;
        }
    }

    private static Map<String, FoundAndTotal> addGrowth(org.unicode.cldr.util.Factory latestFactory, String dir, Matcher matcher, boolean showMissing) {
        final File mainDir = new File(dir + "/common/main/");
        final File annotationDir = new File(dir + "/common/annotations/");
        File[] paths = annotationDir.exists() ? new File[] {mainDir, annotationDir} : new File[] {mainDir};
        org.unicode.cldr.util.Factory newFactory;
        try {
            newFactory = SimpleFactory.make(paths, ".*");
        } catch (RuntimeException e1) {
            throw e1;
        }
        Map<String, FoundAndTotal> data = new HashMap<>();
        char c = 0;
        Set<String> latestAvailable = newFactory.getAvailableLanguages();
        for (String locale : newFactory.getAvailableLanguages()) {
            if (!matcher.reset(locale).matches()) {
                continue;
            }
            if (!latestAvailable.contains(locale)) {
                continue;
            }
            if (SUPPLEMENTAL_DATA_INFO.getDefaultContentLocales().contains(locale)
                || locale.equals("root")
                || locale.equals("supplementalData")) {
                continue;
            }
            char nc = locale.charAt(0);
            if (nc != c) {
                System.out.println("\t" + locale);
                c = nc;
            }
            if (DEBUG_FILTER != 0 && DEBUG_FILTER != nc) {
                continue;
            }
            CLDRFile latestFile = null;
            try {
                latestFile = latestFactory.make(locale, true);
            } catch (Exception e2) {
                System.out.println("Can't make latest CLDRFile for: " + locale + "\tlatest: " + Arrays.asList(latestFactory.getSourceDirectories()));
                continue;
            }
            CLDRFile file = null;
            try {
                file = newFactory.make(locale, true);
            } catch (Exception e2) {
                System.out.println("Can't make CLDRFile for: " + locale + "\tpast: " + mainDir);
                continue;
            }
            // HACK check bogus
//            Collection<String> extra = file.getExtraPaths();
//
//            final Iterable<String> fullIterable = file.fullIterable();
//            for (String path : fullIterable) {
//                if (path.contains("\"one[@")) {
//                    boolean inside = extra.contains(path);
//                    Status status = new Status();
//                    String loc = file.getSourceLocaleID(path, status );
//                    int debug = 0;
//                }
//            }
            // END HACK
            Counter<Level> foundCounter = new Counter<>();
            Counter<Level> unconfirmedCounter = new Counter<>();
            Counter<Level> missingCounter = new Counter<>();
            Set<String> unconfirmedPaths = null;
            Relation<MissingStatus, String> missingPaths = null;
            unconfirmedPaths = new LinkedHashSet<>();
            missingPaths = Relation.of(new LinkedHashMap(), LinkedHashSet.class);
            VettingViewer.getStatus(latestFile.fullIterable(), file,
                pathHeaderFactory, foundCounter, unconfirmedCounter,
                missingCounter, missingPaths, unconfirmedPaths);

            // HACK
            Set<Entry<MissingStatus, String>> missingRemovals = new HashSet<>();
            for (Entry<MissingStatus, String> e : missingPaths.keyValueSet()) {
                if (e.getKey() == MissingStatus.ABSENT) {
                    final String path = e.getValue();
                    if (HACK.get(path) != null) {
                        missingRemovals.add(e);
                        missingCounter.add(Level.MODERN, -1);
                        foundCounter.add(Level.MODERN, 1);
                    } else {
                        Status status = new Status();
                        String loc = file.getSourceLocaleID(path, status);
                        int debug = 0;
                    }
                }
            }
            for (Entry<MissingStatus, String> e : missingRemovals) {
                missingPaths.remove(e.getKey(), e.getValue());
            }
            // END HACK

            if (showMissing) {
                int count = 0;
                for (String s : unconfirmedPaths) {
                    System.out.println(++count + "\t" + locale + "\tunconfirmed\t" + s);
                }
                for (Entry<MissingStatus, String> e : missingPaths.keyValueSet()) {
                    String path = e.getValue();
                    Status status = new Status();
                    String loc = file.getSourceLocaleID(path, status);
                    int debug = 0;

                    System.out.println(++count + "\t" + locale + "\t" + CldrUtility.toString(e));
                }
                int debug = 0;
            }

            data.put(locale, new FoundAndTotal(foundCounter, unconfirmedCounter, missingCounter));
        }
        return Collections.unmodifiableMap(data);
    }

    public static void showCoverage(Anchors anchors, Matcher matcher) throws IOException {
        showCoverage(anchors, matcher, null, false);
    }

    public static void showCoverage(Anchors anchors, Matcher matcher, Set<String> locales, boolean useOrgLevel) throws IOException {
        final String title = "Locale Coverage";
        try (PrintWriter pw = new PrintWriter(new FormattedFileWriter(null, title, null, anchors));
            PrintWriter tsv_summary = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-coverage.tsv");
            PrintWriter tsv_missing = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-missing.tsv");
            PrintWriter tsv_missing_summary = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-missing-summary.tsv");
            PrintWriter tsv_missing_basic = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-missing-basic.tsv");
            ){
            tsv_missing_summary.println(TSV_MISSING_SUMMARY_HEADER);
            tsv_missing.println(TSV_MISSING_HEADER);
            tsv_missing_basic.println(TSV_MISSING_BASIC_HEADER);

            Set<String> checkModernLocales = STANDARD_CODES.getLocaleCoverageLocales(Organization.cldr, EnumSet.of(Level.MODERN));
            Set<String> availableLanguages = new TreeSet<>(factory.getAvailableLanguages());
            availableLanguages.addAll(checkModernLocales);
            Relation<String, String> languageToRegion = Relation.of(new TreeMap(), TreeSet.class);
            LanguageTagParser ltp = new LanguageTagParser();
            LanguageTagCanonicalizer ltc = new LanguageTagCanonicalizer(true);
            for (String locale : factory.getAvailable()) {
                String country = ltp.set(locale).getRegion();
                if (!country.isEmpty()) {
                    languageToRegion.put(ltc.transform(ltp.getLanguageScript()), country);
                }
            }

            fixCommonLocales();

            System.out.println(Joiner.on("\n").join(languageToRegion.keyValuesSet()));

            System.out.println("# Checking: " + availableLanguages);

            NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.ENGLISH);
            percentFormat.setMaximumFractionDigits(1);

            pw.println("<p style='text-align: left'>This chart shows the coverage levels in this release. "
                + "See also <a href='https://github.com/unicode-org/cldr-staging/tree/main/docs/charts/41/tsv'>TSV Files</a> starting with “locale-”. "
                + "Totals are listed after the main chart.</p>\n"
                + "<blockquote><ul>\n"
                + "<li><a href='#main_table'>Main Table</a></li>\n"
                + "<li><a href='#level_counts'>Level Counts</a></li>\n"
                + "</ul></blockquote>\n"
                + "<h3>Column Key</h3>\n"
                + "<table class='subtle' style='margin-left:3em; margin-right:3em'>\n"
                + "<tr><th>Direct.</th><td>The CLDR source directory</td></tr>\n"
                + "<tr><th>Default Region</th><td>The default region for locale code, based on likely subtags</td></tr>\n"
                + "<tr><th>№ Locales</th><td>Note that the coverage of regional locales inherits from their parents.</td></tr>\n"
                + "<tr><th>Target Level</th><td>The default target Coverage Level in CLDR. "
                + "Particular organizations may have different target levels.</td></tr>\n"
                + "<tr><th>ICU</th><td>Indicates whether included in the current version of ICU</td></tr>\n"
                + "<tr><th>Confirmed</th><td>Confirmed items as a percentage of all supplied items. "
                + "If low, the coverage can be improved by getting multiple organizations to confirm.</td></tr>\n"
                + "<tr><th>🄼%, ⓜ%, ⓑ%, ⓒ%</th><td>Coverage at Levels: 🄼 = Modern, ⓜ = Moderate, ⓑ = Basic, ⓒ = Core. "
                + "The percentage of items at that level and below is computed from <i>confirmed_items/total_items</i>. "
                + "A high-level summary of the meaning of the coverage values is at "
                + "<a target='_blank' href='http://www.unicode.org/reports/tr35/tr35-info.html#Coverage_Levels'>Coverage Levels</a>. "
                + "The Core values are described on <a target='_blank' href='https://cldr.unicode.org/index/cldr-spec/core-data-for-new-locales'>Core Data</a>. "
                + "</td></tr>\n"
                + "<tr><th>Missing Features</th><td>These are not single items, but rather specific features, such as plural rules or unit grammar info. "
                + "They are listed if missing at the computed level.<br>"
                + "Example: <i>ⓜ collation</i> means this feature should be supported at a Moderate level.<br>"
                + "<i>Except for Core, these are not accounted for in the percent values.</i></td></tr>\n"
                + "<tr><th>Computed Level</th><td>Computed from the percentage values, "
                + "taking the first level that meets a threshold (currently 🄼 "
                + percentFormat.format(MODERN_THRESHOLD)
                + ", ⓜ "
                + percentFormat.format(MODERATE_THRESHOLD)
                + ", ⓑ "
                + percentFormat.format(BASIC_THRESHOLD)
                + ").</td></tr>\n"
                + "<tr><th>≡ CLDR Target</th><td>Indicates whether the Computed Level whether it equals the CLDR Target or not.</td></tr>\n"
                + "</table>\n"
                );

            Relation<MissingStatus, String> missingPaths = Relation.of(new EnumMap<MissingStatus, Set<String>>(
                MissingStatus.class), TreeSet.class, CLDRFile.getComparator(DtdType.ldml));
            Set<String> unconfirmed = new TreeSet<>(CLDRFile.getComparator(DtdType.ldml));

            Set<String> defaultContents = SUPPLEMENTAL_DATA_INFO.getDefaultContentLocales();

            Counter<Level> foundCounter = new Counter<>();
            Counter<Level> unconfirmedCounter = new Counter<>();
            Counter<Level> missingCounter = new Counter<>();

            List<Level> levelsToShow = new ArrayList<>(EnumSet.allOf(Level.class));
            levelsToShow.remove(Level.COMPREHENSIVE);
            levelsToShow.remove(Level.UNDETERMINED);
            levelsToShow = ImmutableList.copyOf(levelsToShow);
            List<Level> reversedLevels = new ArrayList<>(levelsToShow);
            Collections.reverse(reversedLevels);
            reversedLevels = ImmutableList.copyOf(reversedLevels);

            int localeCount = 0;

            final TablePrinter tablePrinter = new TablePrinter()
                .addColumn("Direct.", "class='source'", null, "class='source'", true)
                .setBreakSpans(true).setSpanRows(false)
                .addColumn("Language", "class='source'", CldrUtility.getDoubleLinkMsg(), "class='source'", true)
                .setBreakSpans(true)
                .addColumn("English Name", "class='source'", null, "class='source'", true)
                .setBreakSpans(true)
                .addColumn("Native Name", "class='source'", null, "class='source'", true)
                .setBreakSpans(true)
                .addColumn("Script", "class='source'", null, "class='source'", true)
                .setBreakSpans(true)
                .addColumn("Default Region", "class='source'", null, "class='source'", true)
                .setBreakSpans(true)
                .addColumn("Locales", "class='source'", null, "class='targetRight'", true)
                .setBreakSpans(true).setCellPattern("{0,number}")
                .addColumn("CLDR Target", "class='target'", null, "class='target'", true)
                .setBreakSpans(true)
                .addColumn("ICU", "class='target'", null, "class='target'", true)
                .setBreakSpans(true)
                .addColumn("Confirmed", "class='target'", null, "class='targetRight' style='color:gray'", true)
                .setBreakSpans(true).setCellPattern("{0,number,0.0%}")
                ;

            NumberFormat tsvPercent = NumberFormat.getPercentInstance(Locale.ENGLISH);
            tsvPercent.setMaximumFractionDigits(2);

            for (Level level : reversedLevels) {
                String titleLevel = level.getAbbreviation() + "%";
                tablePrinter.addColumn(titleLevel, "class='target'", null, "class='targetRight'", true)
                .setCellPattern("{0,number,0.0%}")
                .setBreakSpans(true);
                switch(level) {
                case CORE:
                    tablePrinter.setSortPriority(2).setSortAscending(false);
                    break;
                case BASIC:
                    tablePrinter.setSortPriority(3).setSortAscending(false);
                    break;
                case MODERATE:
                    tablePrinter.setSortPriority(4).setSortAscending(false);
                    break;
                case MODERN:
                    tablePrinter.setSortPriority(5).setSortAscending(false);
                    break;
                }
            }
            tablePrinter.addColumn("Missing Features", "class='target'", null, "class='target'", true)
            .setBreakSpans(true)
            .addColumn("Computed Level", "class='target'", null, "class='target'", true)
            .setBreakSpans(true).setSortPriority(0).setSortAscending(false)
            .addColumn("≡ CLDR Target", "class='target'", null, "class='target'", true)
            .setBreakSpans(true).setSortPriority(1).setSortAscending(false)
            ;

            long start = System.currentTimeMillis();
            LikelySubtags likelySubtags = new LikelySubtags();

            EnumMap<Level, Double> targetLevel = new EnumMap<>(Level.class);
            targetLevel.put(Level.CORE, 2 / 100d);
            targetLevel.put(Level.BASIC, 16 / 100d);
            targetLevel.put(Level.MODERATE, 33 / 100d);
            targetLevel.put(Level.MODERN, 100 / 100d);

            Multimap<String, String> pathToLocale = TreeMultimap.create();

            int counter = 0;

            Counter<Level> computedLevels = new Counter<>();
            Counter<Level> computedSublocaleLevels = new Counter<>();

            for (String locale : availableLanguages) {
                try {
                    if (locale.contains("supplemental") // for old versionsl
                        || locale.startsWith("sr_Latn")) {
                        continue;
                    }
                    if (locales != null && !locales.contains(locale)) {
                        String base = CLDRLocale.getInstance(locale).getLanguage();
                        if (!locales.contains(base)) {
                            continue;
                        }
                    }
                    if (matcher != null && !matcher.reset(locale).matches()) {
                        continue;
                    }
                    if (defaultContents.contains(locale) || "root".equals(locale) || "und".equals(locale)) {
                        continue;
                    }

                    tsv_missing_summary.flush();
                    tsv_missing.flush();
                    tsv_missing_basic.flush();

                    boolean isSeed = new File(CLDRPaths.SEED_DIRECTORY, locale + ".xml").exists();

                    String region = ltp.set(locale).getRegion();
                    if (!region.isEmpty()) continue; // skip regions

                    final Level cldrLocaleLevelGoal = SC.getLocaleCoverageLevel(Organization.cldr.toString(), locale);
                    final boolean cldrLevelGoalBasicToModern = Level.CORE_TO_MODERN.contains(cldrLocaleLevelGoal);

                    String isCommonLocale = Level.MODERN == cldrLocaleLevelGoal ? "C*"
                        : COMMON_LOCALES.contains(locale) ? "C"
                            : "";

                    String max = likelySubtags.maximize(locale);
                    String script = ltp.set(max).getScript();
                    String defRegion = ltp.getRegion();

                    String language = likelySubtags.minimize(locale);

                    missingPaths.clear();
                    unconfirmed.clear();

                    final CLDRFile file = factory.make(locale, true, minimumDraftStatus);

                    if (locale.equals("af")) {
                        int debug = 0;
                    }

                    Iterable<String> pathSource = new IterableFilter(file.fullIterable());

                    VettingViewer.getStatus(pathSource, file,
                        pathHeaderFactory, foundCounter, unconfirmedCounter,
                        missingCounter, missingPaths, unconfirmed);

                    Set<String> sublocales = languageToRegion.get(language);
                    if (sublocales == null) {
                        sublocales = Collections.EMPTY_SET;
                    }

                    String seedString = isSeed ? "seed" : "common";
                    tablePrinter.addRow()
                    .addCell(seedString)
                    .addCell(language)
                    .addCell(ENGLISH.getName(language))
                    .addCell(file.getName(language))
                    .addCell(script)
                    .addCell(defRegion)
                    .addCell(sublocales.size())
                    .addCell(cldrLocaleLevelGoal == Level.UNDETERMINED ? "" : cldrLocaleLevelGoal.toString())
                    .addCell(getIcuValue(language))
                    ;

                    int sumFound = 0;
                    int sumMissing = 0;
                    int sumUnconfirmed = 0;

                    // get the totals

                    EnumMap<Level, Integer> totals = new EnumMap<>(Level.class);
                    EnumMap<Level, Integer> confirmed = new EnumMap<>(Level.class);
                    Set<CoreItems> specialMissingPaths = EnumSet.noneOf(CoreItems.class);

                    if (locale.equals("af")) {
                        int debug = 0;
                    }

                    Counter<String> starredCounter = new Counter<>();

                    {
                        Multimap<CoreItems, String> detailedErrors = TreeMultimap.create();
                        Set<CoreItems> coverage = CoreCoverageInfo.getCoreCoverageInfo(file, detailedErrors);
                        for (CoreItems item : coverage) {
                            foundCounter.add(item.desiredLevel, 1);
                        }
                        for (Entry<CoreItems, String> entry : detailedErrors.entries()) {
                            CoreItems coreItem = entry.getKey();
                            String path = entry.getValue();
                            specialMissingPaths.add(coreItem);
                            if (cldrLocaleLevelGoal.compareTo(coreItem.desiredLevel) >= 0) {
//                                String line = spreadsheetLine(locale, language, script, "«No " + coreItem + "»", cldrLocaleLevelGoal, coreItem.desiredLevel, "ABSENT", path, null, pathToLocale);
//                                tsv_missing.println(line);
                            } else {
                                gatherStarred(path, starredCounter);
                            }
                            missingCounter.add(coreItem.desiredLevel, 1);
                        }
                    }

                    if (cldrLevelGoalBasicToModern) {
                        Level goalLevel = cldrLocaleLevelGoal;
                        for (Entry<MissingStatus, String> entry : missingPaths.entrySet()) {
                            String path = entry.getValue();
                            String status = entry.getKey().toString();
                            Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                            if (goalLevel.compareTo(foundLevel) >= 0) {
                                String line = spreadsheetLine(locale, language, script, file.getStringValue(path), goalLevel, foundLevel, status, path, file, pathToLocale);
                                tsv_missing.println(line);
                            }
                        }
                        for (String path : unconfirmed) {
                            Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                            if (goalLevel.compareTo(foundLevel) >= 0) {
                                String line = spreadsheetLine(locale, language, script, file.getStringValue(path), goalLevel, foundLevel, "n/a", path, file, pathToLocale);
                                tsv_missing.println(line);
                            }
                        }
                    } else {
                        Level goalLevel = Level.BASIC;
                        for (Entry<MissingStatus, String> entry : missingPaths.entrySet()) {
                            String path = entry.getValue();
                            String status = entry.getKey().toString();
                            Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                            if (goalLevel.compareTo(foundLevel) >= 0) {
                                gatherStarred(path, starredCounter);
                            }
                        }
                        for (String path : unconfirmed) {
                            Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                            if (goalLevel.compareTo(foundLevel) >= 0) {
                                gatherStarred(path, starredCounter);
                            }
                        }
                    }

                    tsv_missing_basic.println(TSV_MISSING_BASIC_HEADER);
                    for (R2<Long, String> starred : starredCounter.getEntrySetSortedByCount(false, null)) {
                        tsv_missing_basic.println(locale + "\t" + starred.get0() + "\t" + starred.get1().replace("\"*\"", "'*'"));
                    }

                    for (Level level : levelsToShow) {
                        long foundCount = foundCounter.get(level);
                        long unconfirmedCount = unconfirmedCounter.get(level);
                        long missingCount = missingCounter.get(level);

                        sumFound += foundCount;
                        sumUnconfirmed += unconfirmedCount;
                        sumMissing += missingCount;

                        confirmed.put(level, sumFound);
                        totals.put(level, sumFound + sumUnconfirmed + sumMissing);
                    }

                    // double modernTotal = totals.get(Level.MODERN);

                    tablePrinter
                    .addCell(sumFound/(double)(sumFound+sumUnconfirmed))
                    ;

                    // first get the accumulated values
                    EnumMap<Level, Integer> accumTotals = new EnumMap<>(Level.class);
                    EnumMap<Level, Integer> accumConfirmed = new EnumMap<>(Level.class);
                    int currTotals = 0;
                    int currConfirmed = 0;
                    for (Level level : levelsToShow) {
                        currTotals += totals.get(level);
                        currConfirmed += confirmed.get(level);
                        accumConfirmed.put(level, currConfirmed);
                        accumTotals.put(level, currTotals);
                    }

                    // print the totals

                    Level computed = Level.UNDETERMINED;

                    for (Level level : reversedLevels) {
                        if (useOrgLevel && cldrLocaleLevelGoal != level) {
                            continue;
                        }
                        int confirmedCoverage = accumConfirmed.get(level);
                        double total = accumTotals.get(level);

                        final double proportion = confirmedCoverage / total;
                        tablePrinter.addCell(proportion);

                        if (computed == Level.UNDETERMINED) {
                            switch (level) {
                            case MODERN:
                                if (proportion >= MODERN_THRESHOLD) {
                                    computed = level;
                                }
                                break;
                            case MODERATE:
                                if (proportion >= MODERATE_THRESHOLD) {
                                    computed = level;
                                }
                                break;
                            case BASIC:
                                if (proportion >= BASIC_THRESHOLD) {
                                    computed = level;
                                }
                                break;
                            default:
                                break;
                            }
                        }
                    }

                    Set<CoreItems> shownMissingPaths = EnumSet.noneOf(CoreItems.class);
                    Level imputedWithCore = computed == Level.UNDETERMINED ? Level.CORE : computed;
                    for (CoreItems item : specialMissingPaths) {
                        if (item.desiredLevel.compareTo(imputedWithCore) <= 0) {
                            shownMissingPaths.add(item);
                        } else {
                            int debug = 0;
                        }
                    }
                    String coreMissingString = Joiner.on(", ").join(shownMissingPaths);

                    String visibleImputed = computed == Level.UNDETERMINED ? "" : computed.toString();
                    computedLevels.add(computed, 1);
                    computedSublocaleLevels.add(computed, sublocales.size());

                    tablePrinter
                    .addCell(coreMissingString)
                    .addCell(visibleImputed)
                    .addCell(computed == cldrLocaleLevelGoal ? " ≡" : " ≠")
                    .finishRow();

                    localeCount++;
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            }
            pw.println("<h3><a name='main_table' href='#main_table'>Main Table</a></h3>");
            pw.println(tablePrinter.toTable());

            pw.println(
                "<h3><a name='level_counts' href='#level_counts'>Level Counts</a></h3>\n"
                + "<table class='subtle'>\n"
                + "<tr><th>" + "Level" + "</th><th>" + "Languages" + "</th><th>" + "Locales" + "</th></tr>"
                );
            int totalCount = 0;
            int totalLocaleCount = 0;
            for (Level level : Lists.reverse(Arrays.asList(Level.values()))) {
                final long count = computedLevels.get(level);
                final long localesCount = computedSublocaleLevels.get(level);
                if (count == 0 || level == Level.UNDETERMINED) {
                    continue;
                }
                totalCount += count;
                totalLocaleCount += localesCount;
                String visibleImputed = level == Level.UNDETERMINED ? "<" + Level.BASIC.toString() : level.toString();
                pw.println("<tr><th>" + visibleImputed + "</th><td class='targetRight'>" + count + "</td><td class='targetRight'>" + localesCount + "</td></tr>");
            }
            pw.println(
                "<tr><th>" + "Total" + "</th><td class='targetRight'>" + totalCount + "</td><td class='targetRight'>" + totalLocaleCount + "</td></tr>\n"
                + "</table>"
            );

            Multimap<Level, String> levelToLocales = TreeMultimap.create();

            for ( Entry<String, Collection<String>> entry : pathToLocale.asMap().entrySet()) {
                String path = entry.getKey();
                Collection<String> localeSet = entry.getValue();
                levelToLocales.clear();
                for (String locale : localeSet) {
                    Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                    levelToLocales.put(foundLevel, locale);
                }
                String phString = "n/a\tn/a\tn/a\tn/a";
                try {
                    PathHeader ph = pathHeaderFactory.fromPath(path);
                    phString = ph.toString();
                } catch (Exception e) {
                }
                for (Entry<Level, Collection<String>> entry2 : levelToLocales.asMap().entrySet()) {
                    Level level = entry2.getKey();
                    localeSet = entry2.getValue();
                    String s = TSV_MISSING_SUMMARY_HEADER; // check for changes
                    tsv_missing_summary.println(
                        level
                        + "\t" + localeSet.size()
                        + "\t" + Joiner.on(" ").join(localeSet)
                        + "\t" + phString
                        );
                }
            }
            tablePrinter.toTsv(tsv_summary);
            long end = System.currentTimeMillis();
            System.out.println((end - start) + " millis = "
                + ((end - start) / localeCount) + " millis/locale");
            ShowPlurals.appendBlanksForScrolling(pw);
        }
    }

//    public static void showEnglish() {
//        Map<PathHeader,String> sorted = new TreeMap<>();
//        CoverageInfo coverageInfo=CLDRConfig.getInstance().getCoverageInfo();
//        for (String path : ENGLISH) {
////            Level currentLevel = SUPPLEMENTAL_DATA_INFO.getCoverageLevel(path, "en");
//            Level currentLevel=coverageInfo.getCoverageLevel(path, "en");
//            if (currentLevel.compareTo(Level.MINIMAL) <= 0) {
//                PathHeader ph = pathHeaderFactory.fromPath(path);
//                sorted.put(ph, currentLevel + "\t" + ENGLISH.getStringValue(path));
//            }
//        }
//        for (Entry<PathHeader, String> entry : sorted.entrySet()) {
//            System.out.println(entry.getKey() + "\t" + entry.getValue());
//        }
//    }

    static class IterableFilter implements Iterable<String> {
        private Iterable<String> source;

        IterableFilter(Iterable<String> source) {
            this.source = source;
        }

        /**
         * When some paths are defined after submission, we need to change them to COMPREHENSIVE in computing the vetting status.
         */

        static final Set<String> SUPPRESS_PATHS_AFTER_SUBMISSION = ImmutableSet.of(
            "//ldml/localeDisplayNames/languages/language[@type=\"ccp\"]",
            "//ldml/localeDisplayNames/territories/territory[@type=\"XA\"]",
            "//ldml/localeDisplayNames/territories/territory[@type=\"XB\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"y\"]"
            );
        @Override
        public Iterator<String> iterator() {
            return new IteratorFilter(source.iterator());
        }

        static class IteratorFilter implements Iterator<String> {
            Iterator<String> source;
            String peek;

            public IteratorFilter(Iterator<String> source) {
                this.source = source;
                fillPeek();
            }
            @Override
            public boolean hasNext() {
                return peek != null;
            }
            @Override
            public String next() {
                String result = peek;
                fillPeek();
                return result;
            }

            private void fillPeek() {
                peek = null;
                while (source.hasNext()) {
                    peek = source.next();
                    // if it is ok to assess, then break
                    if (!SUPPRESS_PATHS_AFTER_SUBMISSION.contains(peek)
                        && SUPPRESS_PATHS_CAN_BE_EMPTY.get(peek) != Boolean.TRUE) {
                        break;
                    }
                    peek = null;
                }
            }
        }

    }
    static final CoverageInfo coverageInfo = new CoverageInfo(SUPPLEMENTAL_DATA_INFO);

// userInfo.getVoterInfo().getLevel().compareTo(VoteResolver.Level.tc)
    static final VoterInfo dummyVoterInfo = new VoterInfo(Organization.cldr, org.unicode.cldr.util.VoteResolver.Level.vetter, "somename");

    static final UserInfo dummyUserInfo = new UserInfo() {
        @Override
        public VoterInfo getVoterInfo() {
            return dummyVoterInfo;
        }
    };
    static final PathValueInfo dummyPathValueInfo = new PathValueInfo() {
        // pathValueInfo.getCoverageLevel().compareTo(Level.COMPREHENSIVE)
        @Override
        public Collection<? extends CandidateInfo> getValues() {
            throw new UnsupportedOperationException();
        }
        @Override
        public CandidateInfo getCurrentItem() {
            throw new UnsupportedOperationException();
        }
        @Override
        public String getBaselineValue() {
            throw new UnsupportedOperationException();
        }
        @Override
        public Level getCoverageLevel() {
            return Level.MODERN;
        }
        @Override
        public boolean hadVotesSometimeThisRelease() {
            throw new UnsupportedOperationException();
        }
        @Override
        public CLDRLocale getLocale() {
            throw new UnsupportedOperationException();
        }
        @Override
        public String getXpath() {
            throw new UnsupportedOperationException();
        }
    };

    public static void gatherStarred(String path, Counter<String> starredCounter) {
        starredCounter.add(new PathStarrer().setSubstitutionPattern("*").set(path), 1);
    }

    public static String spreadsheetLine(String locale, String language, String script, String nativeValue, Level cldrLocaleLevelGoal,
        Level itemLevel, String status, String path, CLDRFile resolvedFile,
        Multimap<String, String> pathToLocale) {
        if (pathToLocale != null) {
            pathToLocale.put(path, locale);
        }
        String stLink = "n/a";
        String englishValue = "n/a";
        StatusAction action = null;
        SurveyToolStatus surveyToolStatus = null;
        String icuValue = getIcuValue(locale);

        String bailey = resolvedFile == null ? "" : resolvedFile.getStringValue(path);

        String phString = "na\tn/a\tn/a\t" + path;
        try {
            PathHeader ph = pathHeaderFactory.fromPath(path);
            phString = ph.toString();
            stLink = URLS.forXpath(locale, path);
            englishValue = ENGLISH.getStringValue(path);
            action = Phase.SUBMISSION.getShowRowAction(dummyPathValueInfo, InputMethod.DIRECT, ph, dummyUserInfo);
        } catch (Exception e) {

        }

        String line =
            language
            + "\t" + ENGLISH.getName(language)
            + "\t" + ENGLISH.getName("script", script)
            + "\t" + cldrLocaleLevelGoal
            + "\t" + itemLevel
            + "\t" + (surveyToolStatus == null ? "n/a" : surveyToolStatus.toString())
            + "\t" + bailey
            + "\t" + phString
            + "\t" + PathHeader.getUrlForLocalePath(locale, path)
            ;
        return line;
    }



    private static String getIcuValue(String locale) {
        return ICU_Locales.contains(new ULocale(locale)) ? "ICU" : "";
    }

    static final Set<ULocale> ICU_Locales = ImmutableSet.copyOf(ULocale.getAvailableLocales());
    private static CLDRURLS URLS = CONFIG.urls();

}
