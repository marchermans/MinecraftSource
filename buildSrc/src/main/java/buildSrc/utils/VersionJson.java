/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package buildSrc.utils;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VersionJson {

    protected static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Argument.class, new Argument.Deserializer())
            .setPrettyPrinting().create();

    public static VersionJson get(Path path) throws FileNotFoundException {
        return get(path.toFile());
    }

    public static VersionJson get(@Nullable File file) throws FileNotFoundException {
        if (file == null) {
            throw new IllegalArgumentException("VersionJson File can not be null!");
        }
        return get(new FileInputStream(file));
    }

    public static VersionJson get(InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream), VersionJson.class);
    }

    @Nullable
    public Arguments arguments;
    public AssetIndex assetIndex;
    public String assets;
    @Nullable
    public Map<String, Download> downloads;
    public Library[] libraries;

    private List<LibraryDownload> _natives = null;
    private List<Library> _libraries = null;

    public List<LibraryDownload> getNatives() {


        if (_natives == null) {
            Map<String, Entry> natives = new HashMap<>();

            OS os = OS.getCurrent();
            for (Library lib : libraries) {
                if (!lib.isAllowed())
                    continue;
                String key = lib.getArtifact().getGroup() + ':' + lib.getArtifact().getName() + ':' + lib.getArtifact().getVersion();

                if (lib.getNatives() != null && lib.getDownloads().getClassifiers() != null && lib.getNatives().containsKey(os.getName())) {
                    LibraryDownload l = lib.getDownloads().getClassifiers().get(lib.getNatives().get(os.getName()));
                    if (l != null) {
                        natives.put(key, new Entry(2, l));
                    }
                }
            }

            _natives = natives.values().stream().map(Entry::download).collect(Collectors.toList());
        }
        return _natives;
    }

    public List<String> getPlatformJvmArgs() {
        if (arguments == null || arguments.jvm == null)
            return Collections.emptyList();

        return Stream.of(arguments.jvm).filter(arg -> arg.getRules() != null && arg.isAllowed()).
                flatMap(arg -> arg.value.stream()).
                map(s -> {
                    if (s.indexOf(' ') != -1)
                        return "\"" + s + "\"";
                    else
                        return s;
                }).collect(Collectors.toList());
    }

    @Nullable
    public Arguments getArguments() {
        return arguments;
    }

    public AssetIndex getAssetIndex() {
        return assetIndex;
    }

    public String getAssets() {
        return assets;
    }

    @Nullable
    public Map<String, Download> getDownloads() {
        return downloads;
    }

    public Library[] getLibraries() {
        if (this._libraries == null) {
            this._libraries = new ArrayList<>();
            for (Library lib : libraries) {
                if (lib.isAllowed())
                    this._libraries.add(lib);
            }
            this._libraries = ImmutableList.copyOf(this._libraries);
        }

        return _libraries.toArray(new Library[0]);
    }

    public static class Arguments {
        public Argument[] game;
        @Nullable
        public Argument[] jvm;
    }

    public static class Argument extends RuledObject {
        public List<String> value;

        public Argument(@Nullable Rule[] rules, List<String> value) {
            this.rules = rules;
            this.value = value;
        }

        public static class Deserializer implements JsonDeserializer<Argument> {
            @Override
            public Argument deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonPrimitive()) {
                    return new Argument(null, Collections.singletonList(json.getAsString()));
                }

                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("rules") || !obj.has("value"))
                    throw new JsonParseException("Error parsing arguments in version json. File is corrupt or its format has changed.");

                JsonElement val = obj.get("value");
                Rule[] rules = GSON.fromJson(obj.get("rules"), Rule[].class);
                @SuppressWarnings("unchecked")
                List<String> value = val.isJsonPrimitive() ? Collections.singletonList(val.getAsString()) : GSON.fromJson(val, List.class);

                return new Argument(rules, value);
            }
        }
    }

    public static class RuledObject {
        @Nullable
        protected Rule[] rules;

        public boolean isAllowed() {
            if (getRules() != null) {
                for (Rule rule : getRules()) {
                    if (!rule.allowsAction()) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Nullable
        public Rule[] getRules() {
            return rules;
        }
    }

    public static class Rule {
        private String action;
        @org.jetbrains.annotations.Nullable
        private OsCondition os;

        public boolean allowsAction() {
            return (getOs() == null || getOs().platformMatches()) == getAction().equals("allow");
        }

        public String getAction() {
            return action;
        }

        @org.jetbrains.annotations.Nullable
        public OsCondition getOs() {
            return os;
        }
    }

    public static class OsCondition {
        @Nullable
        private String name;
        @Nullable
        private String version;
        @Nullable
        private String arch;

        public boolean nameMatches() {
            return getName() == null || OS.getCurrent().getName().equals(getName());
        }

        public boolean versionMatches() {
            return getVersion() == null || Pattern.compile(getVersion()).matcher(System.getProperty("os.version")).find();
        }

        public boolean archMatches() {
            return getArch() == null || Pattern.compile(getArch()).matcher(System.getProperty("os.arch")).find();
        }

        public boolean platformMatches() {
            return nameMatches() && versionMatches() && archMatches();
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Nullable
        public String getVersion() {
            return version;
        }

        @Nullable
        public String getArch() {
            return arch;
        }
    }

    public static class AssetIndex extends Download {
        private String id;
        private int totalSize;

        public String getId() {
            return id;
        }

        public int getTotalSize() {
            return totalSize;
        }
    }

    public static class Download {
        private String sha1;
        private int size;
        private URL url;

        public String getSha1() {
            return sha1;
        }

        public int getSize() {
            return size;
        }

        public URL getUrl() {
            return url;
        }
    }

    public static class LibraryDownload extends Download {
        private String path;

        public String getPath() {
            return path;
        }
    }

    public static class Downloads {
        @Nullable
        private Map<String, LibraryDownload> classifiers;
        @Nullable
        private LibraryDownload artifact;

        @Nullable
        public Map<String, LibraryDownload> getClassifiers() {
            return classifiers;
        }

        @Nullable
        public LibraryDownload getArtifact() {
            return artifact;
        }
    }

    public static class Library extends RuledObject {
        //Extract? rules?
        private String name;
        private Map<String, String> natives;
        private Downloads downloads;
        private Artifact _artifact;

        public Artifact getArtifact() {
            if (_artifact == null) {
                _artifact = Artifact.from(getName());
            }
            return _artifact;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getNatives() {
            return natives;
        }

        public Downloads getDownloads() {
            return downloads;
        }
    }

    public enum OS {
        WINDOWS("windows", "win"),
        LINUX("linux", "linux", "unix"),
        OSX("osx", "mac"),
        UNKNOWN("unknown");

        private final String name;
        private final String[] keys;

        OS(String name, String... keys) {
            this.name = name;
            this.keys = keys;
        }

        public String getName() {
            return this.name;
        }

        public static OS getCurrent() {
            String prop = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            for (OS os : OS.values()) {
                for (String key : os.keys) {
                    if (prop.contains(key)) {
                        return os;
                    }
                }
            }
            return UNKNOWN;
        }
    }

    private static final class Entry {
        private final int priority;
        private final LibraryDownload download;

        Entry(int priority, LibraryDownload download) {
            this.priority = priority;
            this.download = download;
        }

        public int priority() {
            return priority;
        }

        public LibraryDownload download() {
            return download;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final Entry that = (Entry) obj;
            return this.priority == that.priority &&
                    Objects.equals(this.download, that.download);
        }

        @Override
        public int hashCode() {
            return Objects.hash(priority, download);
        }

        @Override
        public String toString() {
            return "Entry[" +
                    "priority=" + priority + ", " +
                    "download=" + download + ']';
        }
    }
}
