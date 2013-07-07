/*
 * ====================================================================
 * This software is subject to the terms of the Common Public License
 * Agreement, available at the following URL:
 *   http://www.opensource.org/licenses/cpl.html .
 * You must accept the terms of that agreement to use this software.
 * ====================================================================
 */
package com.eyeq.pivot4j.analytics.repository.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eyeq.pivot4j.analytics.repository.ReportContent;
import com.eyeq.pivot4j.analytics.repository.RepositoryFile;
import com.eyeq.pivot4j.analytics.repository.RepositoryFileComparator;

public class TestRepositoryImpl implements TestRepository {

	private static final String RESOURCE_PREFIX = "/com/eyeq/pivot4j/analytics/repository/test";

	private Logger logger = LoggerFactory.getLogger(getClass());

	private TestFile root;

	private Map<String, TestFile> files;

	private Map<String, ReportContent> contents;

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.test.TestRepository#initialize()
	 */
	@Override
	@PostConstruct
	public void initialize() {
		if (logger.isInfoEnabled()) {
			logger.info("Initializing test repository content.");
		}

		this.root = new TestFile();

		this.files = new HashMap<String, TestFile>();
		this.contents = new HashMap<String, ReportContent>();

		addFile(root);

		TestFile basic = new TestFile("Basic Tests", root, true);
		TestFile advanced = new TestFile("Advanced Tests", root, true);
		TestFile aggregation = new TestFile("Aggregation", advanced, true);
		TestFile ragged = new TestFile("Ragged Dimension", advanced, true);
		TestFile properties = new TestFile("Properties", advanced, true);

		addFile(basic);
		addFile(advanced);
		addFile(aggregation);
		addFile(properties);
		addFile(ragged);

		addFile(new TestFile("Simple", basic, false));
		addFile(new TestFile("Simple Properties", properties, false));
		addFile(new TestFile("Ragged Test", ragged, false));
	}

	/**
	 * @param file
	 */
	protected synchronized void addFile(TestFile file) {
		if (logger.isInfoEnabled()) {
			logger.info("Adding file to repository : " + file);
		}

		files.put(file.getPath(), file);
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#getRoot()
	 */
	@Override
	public RepositoryFile getRoot() {
		return root;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#getFile(java.lang.String)
	 */
	@Override
	public RepositoryFile getFile(String path) {
		return files.get(path);
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#exists(java.lang.String)
	 */
	@Override
	public boolean exists(String path) {
		return files.containsKey(path);
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#getFiles(com.eyeq.pivot4j.analytics.repository.RepositoryFile)
	 */
	@Override
	public synchronized List<RepositoryFile> getFiles(RepositoryFile parent)
			throws IOException {
		List<RepositoryFile> children = new LinkedList<RepositoryFile>();

		for (RepositoryFile child : files.values()) {
			if (parent.equals(child.getParent())) {
				children.add(child);
			}
		}

		Collections.sort(children, new RepositoryFileComparator());

		return children;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#createDirectory(com.eyeq.pivot4j.analytics.repository.RepositoryFile,
	 *      java.lang.String)
	 */
	@Override
	public synchronized RepositoryFile createDirectory(RepositoryFile parent,
			String name) throws IOException {
		TestFile directory = new TestFile(name, (TestFile) parent, true);

		addFile(directory);

		return directory;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#createFile(com.eyeq.pivot4j.analytics.repository.RepositoryFile,
	 *      java.lang.String,
	 *      com.eyeq.pivot4j.analytics.repository.ReportContent)
	 */
	@Override
	public synchronized RepositoryFile createFile(RepositoryFile parent,
			String name, ReportContent content) throws IOException,
			ConfigurationException {
		TestFile file = new TestFile(name, (TestFile) parent, false);

		String path = file.getPath();

		if (content == null) {
			contents.remove(path);
		} else {
			contents.put(path, content);
		}

		files.put(path, file);

		return file;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#renameFile(com.eyeq.pivot4j.analytics.repository.RepositoryFile,
	 *      java.lang.String)
	 */
	@Override
	public synchronized RepositoryFile renameFile(RepositoryFile file,
			String newName) throws IOException {
		TestFile newFile = new TestFile(newName, (TestFile) file.getParent(),
				file.isDirectory());

		String oldPath = file.getPath();
		String newPath = newFile.getPath();

		if (file.isDirectory()) {
			List<String> names = new LinkedList<String>(files.keySet());

			String parentPath = file.getPath();

			for (String path : names) {
				if (path.startsWith(parentPath)) {
					TestFile child = files.remove(path);
					ReportContent content = contents.remove(path);

					if (child.equals(newFile)) {
						child.setName(newName);
					}

					for (RepositoryFile ancestor : child.getAncestors()) {
						if (ancestor.equals(newFile)) {
							((TestFile) ancestor).setName(newName);
							break;
						}
					}

					files.put(child.getPath(), child);

					if (content != null) {
						contents.put(child.getPath(), content);
					}
				}
			}
		} else {
			files.remove(oldPath);
			files.put(newPath, newFile);

			ReportContent content = contents.remove(oldPath);

			if (content != null) {
				contents.put(newPath, content);
			}
		}

		return newFile;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#getContent(com.eyeq.pivot4j.analytics.repository.RepositoryFile)
	 */
	@Override
	public synchronized ReportContent getContent(RepositoryFile file)
			throws IOException, ConfigurationException {
		String path = RESOURCE_PREFIX + file.getPath();

		if (logger.isInfoEnabled()) {
			logger.info("Opening file : " + path);
		}

		ReportContent content = contents.get(path);

		if (content == null) {
			InputStream in = getClass().getResourceAsStream(path);

			try {
				content = new ReportContent(in);
			} finally {
				if (in != null) {
					in.close();
				}
			}

			contents.put(path, content);
		}

		return content;
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#setContent(com.eyeq.pivot4j.analytics.repository.RepositoryFile,
	 *      com.eyeq.pivot4j.analytics.repository.ReportContent)
	 */
	@Override
	public synchronized void setContent(RepositoryFile file,
			ReportContent content) throws IOException, ConfigurationException {
		contents.put(file.getPath(), content);
	}

	/**
	 * @see com.eyeq.pivot4j.analytics.repository.ReportRepository#deleteFile(com.eyeq.pivot4j.analytics.repository.RepositoryFile)
	 */
	@Override
	public synchronized void deleteFile(RepositoryFile file) throws IOException {
		List<String> names = new LinkedList<String>(files.keySet());

		String parentPath = file.getPath();

		for (String path : names) {
			if (path.startsWith(parentPath)) {
				files.remove(path);
				contents.remove(path);
			}
		}
	}
}