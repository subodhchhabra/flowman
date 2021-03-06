/*
 * Copyright 2018 Kaya Kupferschmidt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimajix.flowman.spec.task

import java.io.File
import java.io.IOException
import java.nio.charset.Charset

import ch.ethz.ssh2.Connection
import ch.ethz.ssh2.InteractiveCallback
import ch.ethz.ssh2.KnownHosts
import ch.ethz.ssh2.SFTPException
import ch.ethz.ssh2.SFTPOutputStream
import ch.ethz.ssh2.SFTPv3Client
import ch.ethz.ssh2.ServerHostKeyVerifier
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.IOUtils
import org.slf4j.LoggerFactory

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.execution.Executor
import com.dimajix.flowman.fs.FileSystem
import com.dimajix.flowman.spec.ConnectionIdentifier
import com.dimajix.flowman.spec.connection.SshConnection
import com.dimajix.flowman.util.tryWith


object SftpUploadTask {
    private val logger = LoggerFactory.getLogger(classOf[SftpUploadTask])
    /**
      * The current implementation of @see KnownHosts does not support
      * known_hosts entries that use a non-default port.
      * If we encounter such an entry we wrap it into the known_hosts
      * format before looking it up.
      */
    private class PortAwareKnownHosts(val knownHosts: File) extends KnownHosts(knownHosts) {
        def verifyHostkey(hostname: String, port: Int, serverHostKeyAlgorithm: String, serverHostKey: Array[Byte]): Int = {
            logger.debug(s"Verifying remote server $hostname:$port with algorithm $serverHostKeyAlgorithm")
            val finalHostname = if (port != 22)
                s"[$hostname]:$port"
            else
                hostname
            super.verifyHostkey(finalHostname, serverHostKeyAlgorithm, serverHostKey)
        }
    }
}


class SftpUploadTask extends BaseTask {
    import SftpUploadTask._
    private val logger = LoggerFactory.getLogger(classOf[SftpUploadTask])

    @JsonProperty(value="source", required=true) private var _source:String = ""
    @JsonProperty(value="target", required=true) private var _target:String = ""
    @JsonProperty(value="connection", required=true) private var _connection:String = ""
    @JsonProperty(value="merge", required=false) private var _merge:String = "false"
    @JsonProperty(value="delimiter", required=true) private var _delimiter:String = _
    @JsonProperty(value="overwrite", required=false) private var _overwrite:String = "true"

    def source(implicit context:Context) : String = context.evaluate(_source)
    def target(implicit context:Context) : String = context.evaluate(_target)
    def connection(implicit context: Context) : ConnectionIdentifier = ConnectionIdentifier.parse(context.evaluate(_connection))
    def merge(implicit context:Context) : Boolean = context.evaluate(_merge).toBoolean
    def delimiter(implicit context:Context) : String = context.evaluate(_delimiter)
    def overwrite(implicit context:Context) : Boolean = context.evaluate(_overwrite).toBoolean

    override def execute(executor:Executor) : Boolean = {
        implicit val context = executor.context
        val credentials = context.getConnection(this.connection).asInstanceOf[SshConnection]
        val host = credentials.host
        val port = Some(credentials.port).filter(_ > 0).getOrElse(22)
        val fs = FileSystem(executor.hadoopConf)
        val src = fs.file(source)
        val dst = new Path(target)
        val delimiter = Option(this.delimiter).filter(_.nonEmpty).map(_.getBytes(Charset.forName("UTF-8")))
        logger.info(s"Uploading '$src' to remote destination 'sftp://$host:$port/$dst' (overwrite=$overwrite)")

        if (!src.exists()) {
            logger.error(s"Source '$src' does not exist")
            throw new IOException(s"Source '$src' does not exist")
        }

        val connection = connect(host, port, credentials)
        try {
            val client = new SFTPv3Client(connection)
            try {
                if (!overwrite && exists(client, dst)) {
                    logger.error(s"Target file already exists at 'sftp://$host:$port/$dst'")
                    throw new IOException(s"Target file already exists at 'sftp://$host:$port/$dst'")
                }

                if (src.isDirectory()) {
                    if (merge)
                        uploadMergedFile(client, src, dst, delimiter)
                    else
                        uploadDirectory(client, src, dst)
                }
                else {
                    uploadSingleFile(client, src, dst)
                }
            }
            finally {
                client.close()
            }
        }
        finally {
            connection.close()
        }

        true
    }

    private def uploadSingleFile(client:SFTPv3Client, src:com.dimajix.flowman.fs.File, dst:Path) : Unit = {
        logger.info(s"Uploading file '$src' to sftp remote destination '$dst'")
        ensureDirectory(client, dst.getParent)
        tryWith(src.open()) { input =>
            val handle = client.createFile(dst.toString)
            tryWith(new SFTPOutputStream(handle)) { output =>
                IOUtils.copyBytes(input, output, 16384)
            }
            client.closeFile(handle)
        }
    }

    private def uploadMergedFile(client:SFTPv3Client, src:com.dimajix.flowman.fs.File, dst:Path, delimiter:Option[Array[Byte]]) : Unit = {
        logger.info(s"Uploading merged directory '$src' to sftp remote destination '$dst'")
        ensureDirectory(client, dst.getParent)
        val handle = client.createFile(dst.toString)
        tryWith(new SFTPOutputStream(handle)) { output =>
            src.list()
                .filter(_.isFile())
                .sortBy(_.toString)
                .foreach { file =>
                    tryWith(file.open()) { input =>
                        IOUtils.copyBytes(input, output, 16384)
                        delimiter.foreach(output.write)
                    }
                }
        }
        client.closeFile(handle)
    }

    private def uploadDirectory(client:SFTPv3Client, src:com.dimajix.flowman.fs.File, dst:Path) : Unit = {
        logger.info(s"Uploading directory '$src' to sftp remote destination '$dst'")
        ensureDirectory(client, dst)
        src.list()
            .filter(_.isFile())
            .foreach(file => {
                uploadSingleFile(client, file, new Path(dst, file.filename))
            })
    }

    private def ensureDirectory(client: SFTPv3Client, path: Path) : Unit = {
        if (!exists(client, path)) {
            if (!path.getParent().isRoot) {
                ensureDirectory(client, path.getParent)
            }
            client.mkdir(path.toString, BigInt("700",8).intValue())
        }
    }

    private def exists(client:SFTPv3Client, file:Path) : Boolean = {
        import ch.ethz.ssh2.sftp.ErrorCodes
        try {
            client.stat(file.toString)
            true
        } catch {
            case e: SFTPException =>
                if (e.getServerErrorCode == ErrorCodes.SSH_FX_NO_SUCH_FILE)
                    false
                else
                    throw e
        }
    }

    private def connect(host:String, port:Int, credentials:SshConnection)(implicit context: Context) : Connection = {
        val username = credentials.username
        val password = credentials.password
        val keyFile = credentials.keyFile
        val keyPassword = credentials.keyPassword

        logger.info(s"Connecting via SFTP to $host:$port")
        val connection = new Connection(host, port)
        connection.connect(hostKeyVerifier(credentials))

        if (password != null && password.nonEmpty) {
            if (connection.isAuthMethodAvailable(username, "password")) {
                logger.debug(s"Using non-interactive password authentication for connecting to $host:$port")
                connection.authenticateWithPassword(username, password)
            }
            else if (connection.isAuthMethodAvailable(username, "keyboard-interactive")) {
                logger.debug(s"Using interactive password authentication for connecting to $host:$port")
                connection.authenticateWithKeyboardInteractive(username, new InteractiveCallback() {
                    @throws[Exception]
                    override def replyToChallenge(name: String, instruction: String, numPrompts: Int, prompt: Array[String], echo: Array[Boolean]): Array[String] = {
                        prompt.length match {
                            case 0 =>
                                return new Array[String](0)
                            case 1 =>
                                return Array[String](password)
                        }
                        logger.error(s"Cannot proceed with keyboard-interactive authentication to $host:$port. Server requested " + prompt.length + " challenges, we only support 1.")
                        throw new IOException(s"Cannot proceed with keyboard-interactive authentication to $host:$port. Server requested " + prompt.length + " challenges, we only support 1.")
                    }
                })
            }
            else {
                logger.error(s"Server at $host:$port does not support any of our supported password authentication methods")
                throw new IOException(s"Server at $host:$port does not support any of our supported password authentication methods")
            }
        }
        else {
            logger.debug(s"Using private key authentication for connecting to $host:$port")
            connection.authenticateWithPublicKey(username, keyFile, keyPassword)
        }

        connection
    }

    private def hostKeyVerifier(credentials:SshConnection)(implicit context: Context) : ServerHostKeyVerifier = {
        val knownHosts = credentials.knownHosts
        if (knownHosts != null) {
            val verifier = new PortAwareKnownHosts(knownHosts)
            new ServerHostKeyVerifier {
                @throws[Exception]
                def verifyServerHostKey(hostname: String, port: Int, serverHostKeyAlgorithm: String, serverHostKey: Array[Byte]): Boolean = {
                    if (verifier.verifyHostkey(hostname, port, serverHostKeyAlgorithm, serverHostKey) == KnownHosts.HOSTKEY_IS_OK) {
                        true
                    }
                    else {
                        logger.error(s"Couldn't verify host key for $hostname:$port")
                        throw new IOException(s"Couldn't verify host key for $hostname:$port")
                    }
                }
            }
        }
        else {
            new ServerHostKeyVerifier {
                override def verifyServerHostKey(s: String, i: Int, s1: String, bytes: Array[Byte]): Boolean = true
            }
        }
    }
}
