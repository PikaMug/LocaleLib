/*
 * MIT License
 *
 * Copyright (c) 2019 PikaMug
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.pikamug.localelib;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("unused")
public class LocaleLib extends JavaPlugin {
    private LocaleManager manager;
    
    @Override
    public void onEnable() {
        print();

        manager = new LocaleManager();
    }

    @Override
    public void onDisable() {
    }

    public void print() {
        listResourceTree("assets/minecraft/lang", "", 0, 3);
        List<String> matchingPaths = searchResources("assets/minecraft/lang", ".json", 3);
        System.out.println("Matching paths:");
        for (String path : matchingPaths) {
            System.out.println(path);
        }
    }

    private void listResourceTree(String folder, String parent, int depth, int maxDepth) {
        System.out.println(getIndentation(depth) + "+" + parent);
        if (depth == maxDepth) return;
        try {
            List<String> resourcePaths = getResourcePaths(folder);
            for (String resourcePath : resourcePaths) {
                if (isResourceDirectory(folder, resourcePath)) {
                    listResourceTree(folder + "/" + resourcePath, resourcePath, depth + 1, maxDepth);
                } else {
                    System.out.println(getIndentation(depth + 1) + "|-" + resourcePath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getResourcePaths(String folder) throws IOException {
        List<String> resourcePaths = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls = loader.getResources(folder);

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if (protocol.equals("jar")) {
                String jarPath = getJarPath(url);
                if (jarPath != null) {
                    try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
                        resourcePaths.addAll(getResourcePathsFromJar(jarFile, folder));
                    }
                }
            } else if (protocol.equals("file")) {
                String filePath = URLDecoder.decode(url.getPath(), "UTF-8");
                try {
                    resourcePaths.addAll(getResourcePathsFromFile(filePath, folder));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return resourcePaths;
    }

    private String getJarPath(URL jarUrl) {
        String path = jarUrl.getPath();
        int separatorIndex = path.indexOf('!');
        if (separatorIndex != -1) {
            return path.substring("file:".length(), separatorIndex);
        }
        return null;
    }

    private List<String> getResourcePathsFromJar(JarFile jarFile, String folder) {
        List<String> resourcePaths = new ArrayList<>();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith(folder + "/")) {
                String resourcePath = entryName.substring(folder.length() + 1);
                if (!resourcePath.isEmpty() && !resourcePath.contains("/")) {
                    resourcePaths.add(resourcePath);
                }
            }
        }

        return resourcePaths;
    }

    private List<String> getResourcePathsFromFile(String filePath, String folder) throws IOException {
        List<String> resourcePaths = new ArrayList<>();
        java.io.File file = new java.io.File(filePath + "/" + folder);
        if (file.exists() && file.isDirectory()) {
            String[] children = file.list();
            if (children != null) {
                for (String child : children) {
                    if (!child.contains("/")) {
                        resourcePaths.add(child);
                    }
                }
            }
        }
        return resourcePaths;
    }

    private boolean isResourceDirectory(String folder, String resourcePath) {
        return resourcePath.indexOf('/') == -1;
    }

    private String getIndentation(int depth) {
        StringBuilder indentation = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indentation.append("  |");
        }
        return indentation.toString();
    }

    private List<String> searchResources(String folder, String searchCriteria, int maxDepth) {
        List<String> matchingPaths = new ArrayList<>();
        try {
            searchResourcesRecursive(folder, "", 0, maxDepth, searchCriteria, matchingPaths);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return matchingPaths;
    }

    private void searchResourcesRecursive(String folder, String parent, int depth, int maxDepth, String searchCriteria, List<String> matchingPaths) throws IOException {
        List<String> resourcePaths = getResourcePaths(folder);
        for (String resourcePath : resourcePaths) {
            if (resourcePath.endsWith(searchCriteria)) {
                matchingPaths.add(getIndentation(depth + 1) + "- " + folder + "/" + resourcePath);
            }
            if (depth < maxDepth && isResourceDirectory(folder, resourcePath)) {
                searchResourcesRecursive(folder + "/" + resourcePath, resourcePath, depth + 1, maxDepth, searchCriteria, matchingPaths);
            }
        }
    }

    public LocaleManager getLocaleManager() {
        return manager;
    }
}
