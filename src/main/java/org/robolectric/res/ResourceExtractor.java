package org.robolectric.res;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;

public class ResourceExtractor extends ResourceIndex {
  private static final ResourceRemapper RESOURCE_REMAPPER = new ResourceRemapper();
  private static final boolean REMAP_RESOURCES = false;

  private final Class<?> processedRFile;
  private final String packageName;
  private Integer maxUsedInt = null;

  public ResourceExtractor() {
    processedRFile = null;
    packageName = "";
  }

  /**
   * Constructs a ResourceExtractor for the Android system resources.
   * @param classLoader
   */
  public ResourceExtractor(ClassLoader classLoader) {
    Class<?> androidRClass;
    try {
      androidRClass = classLoader.loadClass("android.R");
      Class<?> androidInternalRClass = classLoader.loadClass("com.android.internal.R");

      gatherResourceIdsAndNames(androidRClass, "android", true);
      gatherResourceIdsAndNames(androidInternalRClass, "android", false);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    processedRFile = androidRClass;
    packageName = processedRFile.getPackage().getName();
  }

  public ResourceExtractor(ResourcePath resourcePath) {
    packageName = resourcePath.getPackageName();
    if (resourcePath.rClass == null) {
      processedRFile = null;
      return;
    }
    if (REMAP_RESOURCES) RESOURCE_REMAPPER.remapRClass(resourcePath.rClass);
    processedRFile = resourcePath.rClass;
    gatherResourceIdsAndNames(resourcePath.rClass, packageName, true);
  }

  private void gatherResourceIdsAndNames(Class<?> rClass, String packageName, boolean checkForCollisions) {
    for (Class innerClass : rClass.getClasses()) {
      for (Field field : innerClass.getDeclaredFields()) {
        if (field.getType().equals(Integer.TYPE) && Modifier.isStatic(field.getModifiers())) {
          String section = innerClass.getSimpleName();
          int id;
          try {
            id = field.getInt(null);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }

          if (!section.equals("styleable")) {
            String fieldName = field.getName();
            ResName resName = new ResName(packageName, section, fieldName);

            resourceNameToId.put(resName, id);

            if (checkForCollisions && resourceIdToResName.containsKey(id)) {
              String message =
                  id + " is already defined with name: " + resourceIdToResName.get(id)
                      + " can't also call it: " + resName;
              if (REMAP_RESOURCES) {
                throw new RuntimeException(message);
              } else {
                System.err.println(message);
              }
            }

            resourceIdToResName.put(id, resName);
          }
        }
      }
    }
  }

  @Override
  public synchronized Integer getResourceId(ResName resName) {
    Integer id = resourceNameToId.get(resName);
    if (id == null && ("android".equals(resName.packageName) || "".equals(resName.packageName))) {
      if (maxUsedInt == null) {
        maxUsedInt = resourceIdToResName.isEmpty() ? 0 : Collections.max(resourceIdToResName.keySet());
      }
      id = ++maxUsedInt;
      resourceNameToId.put(resName, id);
      resourceIdToResName.put(id, resName);
      System.out.println("INFO: no id mapping found for " + resName.getFullyQualifiedName() + "; assigning ID #0x" + Integer.toHexString(id));
    }
    return id;
  }

  @Override
  public synchronized ResName getResName(int resourceId) {
    return resourceIdToResName.get(resourceId);
  }

  public Class<?> getProcessedRFile() {
    return processedRFile;
  }

  @Override public String toString() {
    return "ResourceExtractor{" +
        "package=" + processedRFile +
        '}';
  }

  public String getPackageName() {
      return packageName;
  }
}