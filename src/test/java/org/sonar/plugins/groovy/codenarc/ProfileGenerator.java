/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.groovy.codenarc;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class ProfileGenerator {

  String getPriority(String rule) {
    try {
      Class clazz = Class.forName(rule);

      if (Modifier.isAbstract(clazz.getModifiers())) {
        System.out.println(".... Skipping as abstract");
        return null;
      }

      Object o = clazz.newInstance();
      Integer priority = (Integer)o.getClass().getMethod("getPriority").invoke(o);

      if (priority == 0) return "CRITICAL";
      if (priority == 1) return "MAJOR";
      return "MINOR";
    }
    catch ( Exception ex ) {
      return null;
    }
  }

  private String decamelRule(String rule) {
    StringBuilder sb = new StringBuilder();

    for(Character c : rule.toCharArray()) {
      if (Character.isUpperCase(c)) {
        if (sb.length() > 0)
          sb.append(' ');

        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }

    return sb.toString();
  }

  private void denarc(String file, Properties messages) throws Exception {
    JarFile jar = new JarFile(file);

    Enumeration<JarEntry> entries = jar.entries();

    StringBuilder rules = new StringBuilder("<rules>");
    StringBuilder profile = new StringBuilder("<profile>\n<name>Sonar way</name>\n<language>groovy</language>\n<rules>\n");

    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();

      if (entry.getName().endsWith("Rule.class")) {
        //System.out.println(entry.getName());

        String rule = entry.getName().replace("/", ".").substring(0, entry.getName().lastIndexOf("."));

        System.out.println("Rule " + rule);
        String priority = getPriority(rule);

        if (priority != null) {
          String ruleNameShort = rule.substring(rule.lastIndexOf(".")+1);
          String ruleNameShortWithoutRule = ruleNameShort.substring(0, ruleNameShort.length()-4);
          String ruleName = rule.substring(0, rule.length() - 4);
          String decamel = decamelRule(ruleNameShort);
          String desc = messages.getProperty(ruleNameShort.substring(0, ruleNameShort.length() - 4) + ".description");


          rules.append(String.format("<rule key='%s' priority='%s'>\n  <name><![CDATA[codenarc: %s]]></name>\n  <configKey><![CDATA[%s]]></configKey>\n  <description><![CDATA[%s]]></description>\n</rule>\n",
                    rule, priority, decamel, ruleNameShortWithoutRule, desc));

          profile.append(String.format("<rule>\n\t<repositoryKey>groovy</repositoryKey>\n\t<key>%s</key>\n\t<priority>%s</priority>\n</rule>\n\n", rule, priority));
        }
      }
    }

    rules.append("</rules>");
    profile.append("</rules>\n</profile>");

    FileWriter fw = new FileWriter("src/main/resources/org/sonar/plugins/groovy/rules.xml");
    fw.write(rules.toString());
    fw.close();

    fw = new FileWriter("src/main/resources/org/sonar/plugins/groovy/profile-sonar-way.xml");
    fw.write(profile.toString());
    fw.close();
  }

  @Test
  public void collectRules() throws Exception {
    if (!new File("src/test/resources").exists()) {
      throw new RuntimeException("No resources directory! Run in the right place!");
    }

    Properties messages = new Properties();
    messages.load(getClass().getResourceAsStream("/codenarc-base-messages.properties"));

    URLClassLoader cl = (URLClassLoader)this.getClass().getClassLoader();

    for(URL url : cl.getURLs()) {
      if (url.getFile().contains("CodeNarc")) {
        denarc(url.getFile(), messages);
      }
    }
  }
}
