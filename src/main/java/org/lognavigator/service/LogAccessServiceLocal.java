package org.lognavigator.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.lognavigator.bean.FileInfo;
import org.lognavigator.bean.LogAccessConfig;
import org.lognavigator.bean.LogAccessConfig.LogAccessType;
import org.lognavigator.exception.LogAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;


/**
 * Service which manages access to logs on local host
 */
@Service
@Qualifier("local")
public class LogAccessServiceLocal implements LogAccessService {
	
	@Autowired
	ConfigService configService;
	
	@Override
	public InputStream executeCommand(String logAccessConfigId, String shellCommand) throws LogAccessException {
		
		// Get the LogAccessConfig
		LogAccessConfig logAccessConfig = configService.getLogAccessConfig(logAccessConfigId);
		
		try {
			// Prepare shellCommand array (depending OS)
			String[] shellCommandArray = null;
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				shellCommandArray = new String[]{"cmd", "/C", shellCommand};
			}
			else {
				shellCommandArray = new String[]{"/bin/sh", "-c", shellCommand};
			}
			
			// Execute the command
			Process process = Runtime.getRuntime().exec(shellCommandArray, null, new File(logAccessConfig.getDirectory()));
			
			// Get and return the result stream
			InputStream resultStream = process.getInputStream();
			InputStream errorStream = process.getErrorStream();
			InputStream sequenceStream = new SequenceInputStream(resultStream, errorStream);
			return sequenceStream;
		}
		catch (IOException e) {
			throw new LogAccessException("Error when executing command " + shellCommand + " to " + logAccessConfig, e);
		}
	}

	@Override
	public void downloadFile(String logAccessConfigId, String fileName, OutputStream downloadOutputStream) throws LogAccessException {
		
		// Get the LogAccessConfig
		LogAccessConfig logAccessConfig = configService.getLogAccessConfig(logAccessConfigId);
		
		// Execute the download
		try {
			File downloadFile = new File(logAccessConfig.getDirectory() + "/" + fileName);
			FileCopyUtils.copy(new FileInputStream(downloadFile), downloadOutputStream);
		}
		catch (IOException e) {
			throw new LogAccessException("Error when executing downloading " + fileName + " on " + logAccessConfig, e);
		}
	}

	@Override
	public Set<FileInfo> listFiles(String logAccessConfigId, String subPath) throws LogAccessException {
		
		// Get the LogAccessConfig
		LogAccessConfig logAccessConfig = configService.getLogAccessConfig(logAccessConfigId);

		// Define target directory
		String targetPath = logAccessConfig.getDirectory();
		if (subPath != null) {
			targetPath += "/" + subPath;
		}
		File targetDirectory = new File(targetPath);

		// Check target directory
		if (!targetDirectory.exists()) {
			throw new LogAccessException("Directory '" + targetPath + "' does not exist");
		}
		if (!targetDirectory.isDirectory()) {
			throw new LogAccessException("'" + targetPath + "' is not a directory");
		}

		// List sub files and folders
		File[] childrenFiles = targetDirectory.listFiles();
		
		// Extract meta-informations
		Set<FileInfo> fileInfos = new TreeSet<FileInfo>();
		for (File childrenFile : childrenFiles) {
			FileInfo fileInfo = new FileInfo();
			fileInfo.setFileName(childrenFile.getName());
			fileInfo.setRelativePath(childrenFile.getPath().substring(logAccessConfig.getDirectory().length() + 1).replace('\\', '/'));
			fileInfo.setDirectory(childrenFile.isDirectory());
			fileInfo.setLastModified(new Date(childrenFile.lastModified()));
			fileInfo.setFileSize(childrenFile.length());
			fileInfo.setLogAccessType(LogAccessType.LOCAL);
			fileInfos.add(fileInfo);
		}
		
		// Return meta-informations about files and folders
		return fileInfos;
	}
}
