package com.slemarchand.script.deployer.internal;

import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.portal.kernel.io.unsync.UnsyncPrintWriter;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.scripting.ScriptingException;
import com.liferay.portal.kernel.scripting.ScriptingExecutor;
import com.liferay.portal.kernel.util.UnsyncPrintWriterPool;
import com.liferay.portal.scripting.ScriptingExecutorRegistry;
import com.liferay.portal.util.PropsValues;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Sébastien Le Marchand
 */
@Component(service = ScriptDeployer.class, immediate = true)
public class ScriptDeployer {
	
	@Activate
	public void activate() {
		
		if(PropsValues.AUTO_DEPLOY_ENABLED) {
			
			File autoDeployDir = new File(PropsValues.AUTO_DEPLOY_DEPLOY_DIR);
			
			for (File file : autoDeployDir.listFiles(f -> f.isFile())) {
				try {
					_processFile(file);
				} catch (Exception e) {
					_log.error(e);
				}
			}
			
			_fileWatcher = new DirectoryWatcher(autoDeployDir) {
				
				@Override
				public void doOnChange(File file) {
					try {
						_processFile(file);
					} catch (Exception e) {
						_log.error(e);
					}
				}
			};		
			
			_fileWatcher.start();
		}
	}
	
	@Deactivate
	public void deactivate() {
		
		_log.debug("deactivate()");
		
		if(_fileWatcher != null) {
			_fileWatcher.stopThread();
		}
	}
	
	private void _processFile(File file) throws IOException, ScriptingException {
		
		String language =_getScriptLanguage(file);
		
		if(language!=null) {
			
			ScriptingExecutor scriptingExecutor = _scriptingExecutorRegistry.getScriptingExecutor(language);
			
			if(scriptingExecutor != null) {
				
				Set<String> allowedClasses = null;
				Map<String, Object> inputObjects = new HashMap<>();
				Set<String> outputNames = null;
				
				UnsyncByteArrayOutputStream unsyncByteArrayOutputStream =
						new UnsyncByteArrayOutputStream();

				UnsyncPrintWriter unsyncPrintWriter = UnsyncPrintWriterPool.borrow(
					unsyncByteArrayOutputStream);

				inputObjects.put("out", unsyncPrintWriter);
				
				scriptingExecutor.eval(allowedClasses, inputObjects, outputNames, file);
				
				unsyncPrintWriter.flush();
				
				String output = unsyncByteArrayOutputStream.toString();
				
				try (Stream<String> stream = Arrays.stream(output.split("\\R+"))) {
			        stream.forEach(line -> _log.info(file.getName() + ": " + line));
				}
				
				file.delete();
			}
		}
	}

	private String _getScriptLanguage(File file) {
		
		String language = null;
		
		if(file.isFile()) {
			String name = file.getName();
			
			String extension = name.substring(name.lastIndexOf('.') + 1);
			
			language = _EXTENSION_LANGUAGE_MAP.get(extension);
			
			if(language == null) {
				language = extension;
			}
		}
		
		return language;
	}

	@Reference
	private ScriptingExecutorRegistry _scriptingExecutorRegistry;
	
	private DirectoryWatcher _fileWatcher;
	
	private final static Map<String, String> _EXTENSION_LANGUAGE_MAP;
	
	static {
		
		 Map<String, String> map = new HashMap<>();
		 
		 map.put("py", "python");
		 map.put("rb", "ruby");
		 map.put("js", "javascript");
		 map.put("bsh", "beanshell");
		
		_EXTENSION_LANGUAGE_MAP = Collections.unmodifiableMap(map);
	}
	
	private final static Log _log = LogFactoryUtil.getLog(ScriptDeployer.class);
}