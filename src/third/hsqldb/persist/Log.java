/* Copyright (c) 2001-2016, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package third.hsqldb.persist;


import java.io.File;
import java.io.IOException;

import third.hsqldb.Database;
import third.hsqldb.HsqlException;
import third.hsqldb.NumberSequence;
import third.hsqldb.Row;
import third.hsqldb.Session;
import third.hsqldb.SqlInvariants;
import third.hsqldb.Table;
import third.hsqldb.HsqlNameManager.HsqlName;
import third.hsqldb.error.Error;
import third.hsqldb.error.ErrorCode;
import third.hsqldb.lib.FileAccess;
import third.hsqldb.scriptio.ScriptReaderBase;
import third.hsqldb.scriptio.ScriptReaderDecode;
import third.hsqldb.scriptio.ScriptReaderText;
import third.hsqldb.scriptio.ScriptWriterBase;
import third.hsqldb.scriptio.ScriptWriterEncode;
import third.hsqldb.scriptio.ScriptWriterText;


/**
 *  This class is responsible for managing some of the database files.
 *  An HSQLDB database consists of
 *  a .properties file, a .script file (contains an SQL script),
 *  a .data file (contains data of cached tables) a .backup file
 *  a .log file and a .lobs file.<p>
 *
 *  When using TEXT tables, a data source for each table is also present.<p>
 *
 *  Notes on OpenOffice.org integration.
 *
 *  A Storage API is used when HSQLDB is integrated into OpenOffice.org. All
 *  file operations on the 4 main files are performed by OOo, which integrates
 *  the contents of these files into its database file. The script format is
 *  always TEXT in this case.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @author Bob Preston (sqlbob@users dot sourceforge.net) - text table support
 * @version 2.3.4
 * @since 1.8.0
 */
public class Log {

    private HsqlDatabaseProperties properties;
    private String                 baseFileName;
    private Database               database;
    private FileAccess             fa;
    ScriptWriterBase               dbLogWriter;
    private String                 scriptFileName;
    private String                 logFileName;
    private boolean                filesReadOnly;
    private long                   maxLogSize;
    private int                    writeDelay;
    private DataFileCache          cache;
    private boolean                isModified;

    Log(Database db) {

        database     = db;
        fa           = db.logger.getFileAccess();
        baseFileName = db.getPath();
        properties   = db.getProperties();
    }

    void initParams() {

        maxLogSize     = database.logger.getLogSize() * 1024L * 1024;
        writeDelay     = database.logger.getWriteDelay();
        filesReadOnly  = database.isFilesReadOnly();
        scriptFileName = baseFileName + Logger.scriptFileExtension;
        logFileName    = baseFileName + Logger.logFileExtension;
    }

    /**
     * When opening a database, the hsqldb.compatible_version property is
     * used to determine if this version of the engine is equal to or greater
     * than the earliest version of the engine capable of opening that
     * database.<p>
     */
    void open() {

        initParams();

        int state = properties.getDBModified();

        switch (state) {

            case HsqlDatabaseProperties.FILES_NEW :
                break;

            case HsqlDatabaseProperties.FILES_MODIFIED :
                database.logger.logInfoEvent("open start - state modified");
                deleteNewAndOldFiles();
                deleteOldTempFiles();

                if (properties.isVersion18()) {
                    if (fa.isStreamElement(scriptFileName)) {
                        processScript();
                    } else {
                        database.schemaManager.createPublicSchema();
                    }

                    HsqlName name = database.schemaManager.findSchemaHsqlName(
                        SqlInvariants.PUBLIC_SCHEMA);

                    if (name != null) {
                        database.schemaManager.setDefaultSchemaHsqlName(name);
                    }
                } else {
                    processScript();
                }

                processLog();
                checkpoint();
                break;

            case HsqlDatabaseProperties.FILES_MODIFIED_NEW :
                database.logger.logInfoEvent("open start - state new files");
                renameNewDataFile();
                renameNewScript();
                deleteLog();
                backupData();
                properties.setDBModified(
                    HsqlDatabaseProperties.FILES_NOT_MODIFIED);

            // continue as non-modified files
            // delete log file as zero length file is possible
            // fall through
            case HsqlDatabaseProperties.FILES_NOT_MODIFIED :
                deleteLog();
                database.logger.logInfoEvent(
                    "open start - state not modified");

                /**
                 * if startup is after a SHUTDOWN SCRIPT and there are CACHED
                 * or TEXT tables, perform a checkpoint so that the .script
                 * file no longer contains CACHED or TEXT table rows.
                 */
                processScript();

                if (!filesReadOnly && isAnyCacheModified()) {
                    properties.setDBModified(
                        HsqlDatabaseProperties.FILES_MODIFIED);
                    checkpoint();
                }
                break;
        }

        if (!filesReadOnly) {
            openLog();
        }
    }

    /**
     * Close all the database files. If script argument is true, no .data
     * or .backup file will remain and the .script file will contain all the
     * data of the cached tables as well as memory tables.
     *
     * This is not used for filesReadOnly databases which use shutdown.
     */
    void close(boolean script) {

        closeLog();
        deleteOldDataFiles();
        deleteOldTempFiles();
        deleteTempFileDirectory();
        writeScript(script);
        database.logger.textTableManager.closeAllTextCaches(script);

        if (cache != null) {
            cache.close();
        }

        // set this one last to save the props
        properties.setProperty(HsqlDatabaseProperties.hsqldb_script_format,
                               database.logger.propScriptFormat);
        properties.setDBModified(HsqlDatabaseProperties.FILES_MODIFIED_NEW);
        deleteLog();

        boolean complete = true;

        if (cache != null) {
            if (script) {
                cache.deleteFile();
                cache.deleteBackup();

                if (fa.isStreamElement(cache.dataFileName)) {
                    database.logger.logInfoEvent("delete .data file failed ");

                    complete = false;
                }

                if (fa.isStreamElement(cache.backupFileName)) {
                    database.logger.logInfoEvent(
                        "delete .backup file failed ");

                    complete = false;
                }
            } else {
                cache.backupDataFile(false);
            }
        }

        if (fa.isStreamElement(logFileName)) {
            database.logger.logInfoEvent("delete .log file failed ");

            complete = false;
        }

        renameNewScript();

        if (complete) {
            properties.setDBModified(
                HsqlDatabaseProperties.FILES_NOT_MODIFIED);
        }
    }

    /**
     * Fast counterpart to close(). Does not perform a checkpoint or a backup
     * of the .data file.
     */
    void shutdown() {

        if (cache != null) {
            cache.release();
        }

        database.logger.textTableManager.closeAllTextCaches(false);
        closeLog();
    }

    /**
     * Deletes the leftovers from any previous unfinished operations.
     */
    void deleteNewAndOldFiles() {

        deleteOldDataFiles();
        fa.removeElement(baseFileName + Logger.dataFileExtension
                         + Logger.newFileExtension);
        fa.removeElement(baseFileName + Logger.backupFileExtension
                         + Logger.newFileExtension);
        fa.removeElement(scriptFileName + Logger.newFileExtension);
    }

    void deleteBackup() {
        fa.removeElement(baseFileName + Logger.backupFileExtension);
    }

    void backupData() {

        DataFileCache.backupFile(database,
                                 baseFileName + Logger.dataFileExtension,
                                 baseFileName + Logger.backupFileExtension,
                                 false);
    }

    void renameNewDataFile() {
        DataFileCache.renameDataFile(database,
                                     baseFileName + Logger.dataFileExtension);
    }

    void renameNewBackup() {

        DataFileCache.renameBackupFile(database,
                                       baseFileName
                                       + Logger.backupFileExtension);
    }

    void renameNewScript() {

        if (fa.isStreamElement(scriptFileName + Logger.newFileExtension)) {
            fa.removeElement(scriptFileName);
            fa.renameElement(scriptFileName + Logger.newFileExtension,
                             scriptFileName);
        }
    }

    boolean renameNewDataFileDone() {

        return fa.isStreamElement(baseFileName + Logger.dataFileExtension)
               && !fa.isStreamElement(baseFileName + Logger.dataFileExtension
                                      + Logger.newFileExtension);
    }

    boolean renameNewScriptDone() {

        return fa.isStreamElement(scriptFileName)
               && !fa.isStreamElement(scriptFileName
                                      + Logger.newFileExtension);
    }

    void deleteNewScript() {
        fa.removeElement(scriptFileName + Logger.newFileExtension);
    }

    void deleteNewBackup() {
        fa.removeElement(baseFileName + Logger.backupFileExtension
                         + Logger.newFileExtension);
    }

    void deleteLog() {
        fa.removeElement(logFileName);
    }

    /**
     * Checks all the caches and returns true if the modified flag is set for any
     */
    boolean isAnyCacheModified() {

        if (cache != null && cache.isModified()) {
            return true;
        }

        return database.logger.textTableManager.isAnyTextCacheModified();
    }

    private boolean checkpoint() {

        if (filesReadOnly) {
            return true;
        }

        boolean result       = checkpointClose();
        boolean reopenResult = checkpointReopen();

        if (!result) {
            database.logger.logSevereEvent(
                "checkpoint failed - see previous error", null);
        }

        return reopenResult;
    }

    /**
     * Performs checkpoint including pre and post operations. Returns to the
     * same state as before the checkpoint.
     */
    void checkpoint(Session session, boolean defrag) {

        if (filesReadOnly) {
            return;
        }

        if (cache == null) {
            defrag = false;
        } else if (forceDefrag()) {
            defrag = true;
        }

        if (defrag) {
            defrag(session);
        } else {
            checkpoint();
        }
    }

    /**
     * Performs checkpoint including pre and post operations. Returns to the
     * same state as before the checkpoint.
     */
    boolean checkpointClose() {

        if (filesReadOnly) {
            return true;
        }

        database.logger.logInfoEvent("checkpointClose start");
        synchLog();
        database.lobManager.synch();
        database.logger.logInfoEvent("checkpointClose synched");
        deleteOldDataFiles();

        try {
            writeScript(false);
            database.logger.logInfoEvent("checkpointClose script done");

            if (cache != null) {
                cache.reset();
                cache.backupDataFile(true);
            }

            properties.setProperty(HsqlDatabaseProperties.hsqldb_script_format,
                                   database.logger.propScriptFormat);
            properties.setDBModified(
                HsqlDatabaseProperties.FILES_MODIFIED_NEW);
        } catch (Throwable t) {

            // backup failed perhaps due to lack of disk space
            deleteNewScript();
            deleteNewBackup();
            database.logger.logSevereEvent("checkpoint failed - recovered", t);

            return false;
        }

        closeLog();
        deleteLog();
        renameNewScript();
        renameNewBackup();

        try {
            properties.setDBModified(
                HsqlDatabaseProperties.FILES_NOT_MODIFIED);
        } catch (Throwable e) {
            database.logger.logSevereEvent(
                "logger.checkpointClose properties file save failed", e);
        }

        database.logger.logInfoEvent("checkpointClose end");

        return true;
    }

    boolean checkpointReopen() {

        if (filesReadOnly) {
            return true;
        }

        database.sessionManager.resetLoggedSchemas();

        try {
            if (cache != null) {
                cache.reopen();
            }

            if (dbLogWriter != null) {
                openLog();
            }
        } catch (Throwable e) {
            return false;
        }

        return true;
    }

    /**
     *  Writes out all the rows to a new file without fragmentation.
     */
    public void defrag(Session session) {

        database.logger.logInfoEvent("defrag start");

        try {

// test
/*
            DataFileDefrag.checkAllTables(database);
*/

//
            synchLog();
            database.lobManager.synch();
            deleteOldDataFiles();

            DataFileDefrag dfd = cache.defrag(session);

            database.persistentStoreCollection.setNewTableSpaces();
            database.schemaManager.setIndexRoots(dfd.getIndexRoots());
            database.sessionManager.resetLoggedSchemas();
        } catch (HsqlException e) {
            throw e;
        } catch (Throwable e) {
            database.logger.logSevereEvent("defrag failure", e);

            throw Error.error(ErrorCode.DATA_FILE_ERROR, e);
        }

// test
/*
        DataFileDefrag.checkAllTables(database);
*/

//
        database.logger.logInfoEvent("defrag end");
    }

    /**
     * Returns true if lost space is above the threshold percentage.
     */
    boolean forceDefrag() {

        long limit = database.logger.propCacheDefragLimit
                     * cache.getFileFreePos() / 100;

        if (limit == 0) {
            return false;
        }

        long floor = database.logger.propFileSpaceValue * 1024L * 1024;

        if (floor > limit) {
            limit = floor;
        }

        long lostSize = cache.getLostBlockSize();

        return lostSize > limit;
    }

    /**
     *
     */
    boolean hasCache() {
        return cache != null;
    }

    /**
     * Responsible for creating the data file cache instance.
     */
    DataFileCache getCache() {

        if (cache == null) {
            cache = new DataFileCache(database, baseFileName);

            cache.open(filesReadOnly);
        }

        return cache;
    }

    void setLogSize(int megas) {
        maxLogSize = megas * 1024L * 1024;
    }

    /**
     * Write delay specifies the frequency of FileDescriptor.sync() calls.
     */
    int getWriteDelay() {
        return writeDelay;
    }

    void setWriteDelay(int delay) {

        writeDelay = delay;

        if (dbLogWriter != null && dbLogWriter.getWriteDelay() != delay) {
            dbLogWriter.forceSync();
            dbLogWriter.stop();
            dbLogWriter.setWriteDelay(delay);
            dbLogWriter.start();
        }
    }

    public void setIncrementBackup(boolean val) {

        if (cache != null) {
            cache.setIncrementBackup(val);
        }
    }

    /**
     * Various writeXXX() methods are used for logging statements.
     * INSERT, DELETE and SEQUENCE statements do not check log size
     */
    void writeOtherStatement(Session session, String s) {

        try {
            dbLogWriter.writeOtherStatement(session, s);
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, getLogFileName());
        }

        if (maxLogSize > 0 && dbLogWriter.size() > maxLogSize) {
            database.logger.setCheckpointRequired();
        }

        setModified();
    }

    void writeInsertStatement(Session session, Row row, Table t) {

        try {
            dbLogWriter.writeInsertStatement(session, row, t);
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, getLogFileName());
        }
    }

    void writeDeleteStatement(Session session, Table t, Object[] row) {

        try {
            dbLogWriter.writeDeleteStatement(session, t, row);
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, getLogFileName());
        }
    }

    void writeSequenceStatement(Session session, NumberSequence s) {

        try {
            dbLogWriter.writeSequenceStatement(session, s);
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, getLogFileName());
        }

        setModified();
    }

    void writeCommitStatement(Session session) {

        try {
            dbLogWriter.writeCommitStatement(session);
        } catch (IOException e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, getLogFileName());
        }

        if (maxLogSize > 0 && dbLogWriter.size() > maxLogSize) {
            database.logger.setCheckpointRequired();
        }

        setModified();
    }

    private void setModified() {

        if (!isModified) {
            database.databaseProperties.setDBModified(
                HsqlDatabaseProperties.FILES_MODIFIED);

            isModified = true;
        }
    }

    void synchLog() {

        if (dbLogWriter != null) {
            dbLogWriter.forceSync();
        }
    }

    /**
     * Wrappers for opening-starting / stopping-closing the log file and
     * writer.
     */
    void openLog() {

        if (filesReadOnly) {
            return;
        }

        Crypto crypto = database.logger.getCrypto();

        try {
            if (crypto == null) {
                dbLogWriter = new ScriptWriterText(database, logFileName,
                                                   false, false, false);
            } else {
                dbLogWriter = new ScriptWriterEncode(database, logFileName,
                                                     crypto);
            }

            dbLogWriter.setWriteDelay(writeDelay);
            dbLogWriter.start();

            isModified = false;
        } catch (Throwable e) {
            throw Error.error(ErrorCode.FILE_IO_ERROR, logFileName);
        }
    }

    void closeLog() {

        if (dbLogWriter != null) {
            database.logger.logDetailEvent("log close size: "
                                           + dbLogWriter.size());
            dbLogWriter.close();
        }
    }

    /**
     * Write the .script file as .script.new.
     */
    void writeScript(boolean full) {

        deleteNewScript();

        ScriptWriterBase scw;
        Crypto           crypto = database.logger.getCrypto();

        if (crypto == null) {
            boolean compressed = database.logger.propScriptFormat == 3;

            scw = new ScriptWriterText(database,
                                       scriptFileName
                                       + Logger.newFileExtension, full,
                                           compressed);
        } else {
            scw = new ScriptWriterEncode(database,
                                         scriptFileName
                                         + Logger.newFileExtension, full,
                                             crypto);
        }

        scw.writeAll();
        scw.close();

        scw = null;
    }

    /**
     * Performs all the commands in the .script file.
     */
    private void processScript() {

        ScriptReaderBase scr = null;

        try {
            Crypto crypto = database.logger.getCrypto();

            if (crypto == null) {
                boolean compressed = database.logger.propScriptFormat == 3;

                scr = new ScriptReaderText(database, scriptFileName,
                                           compressed);
            } else {
                scr = new ScriptReaderDecode(database, scriptFileName, crypto,
                                             false);
            }

            Session session =
                database.sessionManager.getSysSessionForScript(database);

            scr.readAll(session);
            scr.close();
        } catch (Throwable e) {
            if (scr != null) {
                scr.close();

                if (cache != null) {
                    cache.release();
                }

                database.logger.textTableManager.closeAllTextCaches(false);
            }

            database.logger.logWarningEvent("Script processing failure", e);

            if (e instanceof HsqlException) {
                throw (HsqlException) e;
            } else if (e instanceof IOException) {
                throw Error.error(ErrorCode.FILE_IO_ERROR, e);
            } else if (e instanceof OutOfMemoryError) {
                throw Error.error(ErrorCode.OUT_OF_MEMORY);
            } else {
                throw Error.error(ErrorCode.GENERAL_ERROR, e);
            }
        }
    }

    /**
     * Performs all the commands in the .log file.
     */
    private void processLog() {

        if (fa.isStreamElement(logFileName)) {
            boolean fullReplay = database.getURLProperties().isPropertyTrue(
                HsqlDatabaseProperties.hsqldb_full_log_replay);

            ScriptRunner.runScript(database, logFileName, fullReplay);
        }
    }

    void deleteOldDataFiles() {

        if (database.logger.isStoredFileAccess()) {
            return;
        }

        try {
            File   file = new File(database.getCanonicalPath());
            File[] list = file.getParentFile().listFiles();

            if (list == null) {
                return;
            }

            for (int i = 0; i < list.length; i++) {
                if (list[i].getName().startsWith(file.getName())
                        && list[i].getName().endsWith(
                            Logger.oldFileExtension)) {
                    list[i].delete();
                }
            }
        } catch (Throwable t) {}
    }

    void deleteOldTempFiles() {

        try {
            if (database.logger.tempDirectoryPath == null) {
                return;
            }

            File   file = new File(database.logger.tempDirectoryPath);
            File[] list = file.listFiles();

            if (list == null) {
                return;
            }

            for (int i = 0; i < list.length; i++) {
                list[i].delete();
            }
        } catch (Throwable t) {}
    }

    void deleteTempFileDirectory() {

        try {
            if (database.logger.tempDirectoryPath == null) {
                return;
            }

            File file = new File(database.logger.tempDirectoryPath);

            file.delete();
        } catch (Throwable t) {}
    }

    String getLogFileName() {
        return logFileName;
    }
}
