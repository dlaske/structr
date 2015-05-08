/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.files.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.ftpserver.ftplet.FtpFile;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.dynamic.File;

/**
 *
 * @author Axel Morgner
 */
public class StructrFtpFile extends AbstractStructrFtpFile {

	private static final Logger logger = Logger.getLogger(StructrFtpFile.class.getName());

	public StructrFtpFile(final File file) {
		super(file);
	}

//	public StructrFtpFile(final String newPath, final StructrFtpUser user) {
//		super(newPath, user);
//	}
	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public boolean isFile() {
		return true;
	}

	@Override
	public long getSize() {
		try (Tx tx = StructrApp.getInstance().tx()) {
			Long size = ((File) structrFile).getSize();
			return size == null ? 0L : size;
		} catch (FrameworkException fex) {}
		return 0L;
	}

	@Override
	public boolean mkdir() {
		logger.log(Level.INFO, "mkdir()");
		throw new UnsupportedOperationException("Not supported.");
	}

	@Override
	public OutputStream createOutputStream(final long l) throws IOException {
		try (Tx tx = StructrApp.getInstance().tx()) {
			return ((File) structrFile).getOutputStream();
		} catch (FrameworkException fex) {
			logger.log(Level.SEVERE, null, fex);
		}
		return null;
	}

	@Override
	public InputStream createInputStream(final long l) throws IOException {
		try (Tx tx = StructrApp.getInstance().tx()) {
			return ((File) structrFile).getInputStream();
		} catch (FrameworkException fex) {
			logger.log(Level.SEVERE, null, fex);
		}
		return null;
	}

	@Override
	public List<FtpFile> listFiles() {
		logger.log(Level.INFO, "listFiles()");
		return null;
	}

}
