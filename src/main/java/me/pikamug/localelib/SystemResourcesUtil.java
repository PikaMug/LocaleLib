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

import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemResourcesUtil {

    /**
     * Returns the context ClassLoader for this thread. The context ClassLoader may be set by the creator of the thread for use by code running in this thread when loading classes and resources. If not {@link Thread#setContextClassLoader(ClassLoader) set}, the default is to inherit the context class loader from the parent thread.
     * The context ClassLoader of the primordial thread is typically set to the class loader used to load the application.
     * @return The context class loader of the current thread.
     */
    public static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Searches for all resource paths found inside the baseFolder on the loader using the searchCriteria.
     * @param loader The class loader to search on
     * @param baseFolder The resource folder to search in
     * @param searchCriteria The search criteria to apply
     * @return An iterator that iterates over all matching resources
     * @throws IOException if an I/O error has occurred
     */
    public static Iterator<String> findResourcesBySearch(ClassLoader loader, String baseFolder, String searchCriteria) throws IOException {
        Pattern pattern = Pattern.compile(searchCriteria);
        Enumeration<URL> urls = loader.getResources(baseFolder);

        List<String> matchingResources = new ArrayList<>();

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if (protocol.equals("jar")) {
                String jarPath = getJarPath(url);
                if (jarPath != null) {
                    try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
                        matchingResources.addAll(getMatchingResourcesFromJar(jarFile, pattern, baseFolder));
                    }
                }
            }
        }
        if (matchingResources.isEmpty()) {
            Bukkit.getLogger().warning("[LocaleLib] " + baseFolder + " could not be found!");
        }
        return matchingResources.iterator();
    }

    private static String getJarPath(URL jarUrl) {
        String path = jarUrl.getPath();
        int separatorIndex = path.indexOf('!');
        if (separatorIndex != -1) {
            return path.substring("file:".length(), separatorIndex);
        }
        return null;
    }

    private static List<String> getMatchingResourcesFromJar(JarFile jarFile, Pattern pattern, String baseFolder) {
        List<String> matchingResources = new ArrayList<>();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith(baseFolder)) {
                String remainingPath = entryName.substring(baseFolder.length());
                Matcher matcher = pattern.matcher(remainingPath);
                if (matcher.find()) {
                    matchingResources.add(entryName);
                }
            }
        }

        return matchingResources;
    }
}