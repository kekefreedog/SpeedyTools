package speedytools.templates;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;

/** Forge's registry bootstrap requires its classes to be loaded by LaunchClassLoader. */
public class RegistryTestRunner extends BlockJUnit4ClassRunner
{
  public RegistryTestRunner(Class<?> testClass) throws InitializationError
  {
    super(load(testClass));
  }

  private static Class<?> load(Class<?> testClass) throws InitializationError
  {
    Set<URL> urls = new LinkedHashSet<URL>();
    for (ClassLoader parent = testClass.getClassLoader(); parent != null; parent = parent.getParent()) {
      if (parent instanceof URLClassLoader) Collections.addAll(urls, ((URLClassLoader)parent).getURLs());
    }
    LaunchClassLoader loader = new LaunchClassLoader(urls.toArray(new URL[urls.size()]));
    loader.addClassLoaderExclusion("org.junit.");
    loader.addClassLoaderExclusion("org.hamcrest.");
    loader.addClassLoaderExclusion(RegistryTestRunner.class.getName());
    try {
      return loader.loadClass(testClass.getName());
    } catch (ClassNotFoundException ex) {
      throw new InitializationError(ex);
    }
  }
}
