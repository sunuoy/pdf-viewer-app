package com.pdfview.sunuy.services

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

object UmdService {
    fun extractText(file: File): String {
        try {
            val parser = UmdParser(file.absolutePath)
            return parser.getAllContent()
        } catch (e: Exception) {
            return "Error reading UMD file: ${e.message}"
        }
    }

    private class UmdParser(private val bookPath: String) {
        private val UMD_FORMAT = 0xde9a9b89L

        var contentLength = 0
        private var additionalCheck = 0L

        var title = ""
        var author = ""
        var type: Byte = 1

        var chapOff = intArrayOf()
        val chapters = mutableListOf<String>()
        val contentArr = mutableListOf<Content>()

        private var currentPoint = 0L

        init {
            read(bookPath)
        }

        private fun read(umdFile: String) {
            FileInputStream(umdFile).use { fis ->
                val dis = DataInputStream(fis)
                val header = readUInt32(dis)
                if (UMD_FORMAT != header) {
                    throw Exception("Invalid UMD file format.")
                }
                val symbol = ByteArray(1)
                var eof = readBytes(dis, symbol)
                while (eof != -1 && symbol[0] == 0x23.toByte()) { // '#'
                    var id = readInt16(dis).toInt() and 0xFFFF
                    val num3 = readByte(dis)
                    val length = (readByte(dis).toInt() - 5).toByte()
                    readSection(id.toShort(), num3, length, dis)
                    eof = readBytes(dis, symbol)
                    if (id == 0xf1 || id == 10) {
                        id = 0x84
                    }
                    while (eof != -1 && symbol[0] == '$'.toByte()) {
                        val num5 = readUInt32(dis)
                        val num6 = readUInt32(dis) - 9
                        readAdditional(id.toShort(), num5, num6, dis)
                        eof = readBytes(dis, symbol)
                    }
                }
            }
        }

        private fun readSection(id: Short, num3: Byte, length: Byte, dis: DataInputStream) {
            val len = length.toInt() and 0xFF
            when (id.toInt()) {
                1 -> {
                    type = readByte(dis)
                    readInt16(dis) // pkgSeed
                }
                2 -> title = readString(dis, len)
                3 -> author = readString(dis, len)
                11 -> contentLength = readInt32(dis)
                12 -> readUInt32(dis)
                0x81, 0x83, 0x84 -> additionalCheck = readUInt32(dis)
                0x0E, 0x0F -> readByte(dis)
                130 -> {
                    readByte(dis)
                    additionalCheck = readUInt32(dis)
                }
                else -> readBytes(dis, len)
            }
        }

        private fun readAdditional(id: Short, check: Long, length: Long, dis: DataInputStream) {
            val len = length.toInt()
            when (id.toInt()) {
                0x0E -> {
                    readBytes(dis, len)
                }
                0x0F -> return
                0x81 -> {
                    readBytes(dis, len)
                }
                130 -> {
                    readBytes(dis, len)
                }
                0x83 -> {
                    val chapOffLen = len / 4
                    chapOff = IntArray(chapOffLen)
                    for (i in 0 until chapOffLen) {
                        chapOff[i] = readInt32(dis)
                    }
                }
                0x84 -> {
                    if (additionalCheck != check) {
                        contentArr.add(Content(currentPoint, length))
                        readBytes(dis, len)
                        return
                    }
                    val buffer1 = readBytes(dis, len) ?: return
                    var num2 = 0
                    while (num2 < buffer1.size) {
                        val num3 = buffer1[num2].toInt() and 0xFF
                        num2++
                        val temp = ByteArray(num3)
                        for (i in 0 until num3) {
                            if (i + num2 < buffer1.size) {
                                temp[i] = buffer1[i + num2]
                            }
                        }
                        reverseBytes(temp)
                        chapters.add(String(temp, Charsets.UTF_16LE))
                        num2 += num3
                    }
                }
                else -> readBytes(dis, len)
            }
        }

        fun getAllContent(): String {
            if (chapOff.isEmpty()) {
                val sb = StringBuilder()
                for (i in 0 until contentArr.size) {
                    val text = getContentText(i)
                    if (text != null) {
                        sb.append(text)
                    }
                }
                return sb.toString().trim()
            }

            val sb = StringBuilder()
            FileInputStream(bookPath).use { fis ->
                val dis = DataInputStream(fis)
                var nn = 0L
                var tempI = 0
                var copyByte: ByteArray? = null
                var copyLength = 0

                for (j in chapOff.indices) {
                    val start = chapOff[j]
                    val strByteL = if (j < chapOff.size - 1) chapOff[j + 1] - start else contentLength - start
                    if (strByteL <= 0) continue
                    var tempL = 0
                    val strBytes = ByteArray(strByteL)

                    if (copyByte != null) {
                        tempL = copyByte.size - copyLength
                        val toCopy = Math.min(tempL, strByteL)
                        System.arraycopy(copyByte, copyLength, strBytes, 0, toCopy)
                        tempL = toCopy
                        copyLength += toCopy
                        if (copyLength >= copyByte.size) {
                            copyByte = null
                            copyLength = 0
                        }
                    }

                    if (tempL < strByteL) {
                        for (i in tempI until contentArr.size) {
                            val index = contentArr[i].index
                            val skipNum = index - nn
                            if (skipNum > 0) {
                                var skipped = 0L
                                while (skipped < skipNum) {
                                    val sk = dis.skip(skipNum - skipped)
                                    if (sk <= 0) break
                                    skipped += sk
                                }
                            }
                            val length = contentArr[i].length.toInt()
                            nn = index + length
                            val bytes = ByteArray(length)
                            dis.readFully(bytes)

                            val newBytes = ByteArray(0x8000)
                            val inflater = Inflater()
                            inflater.setInput(bytes)
                            val inflatedLen = inflater.inflate(newBytes)
                            inflater.end()

                            val strBytesToCopy = Math.min(strByteL - tempL, inflatedLen)
                            System.arraycopy(newBytes, 0, strBytes, tempL, strBytesToCopy)
                            tempL += strBytesToCopy

                            if (strBytesToCopy < inflatedLen) {
                                copyByte = newBytes.copyOf(inflatedLen)
                                copyLength = strBytesToCopy
                                tempI = i + 1
                                break
                            }
                        }
                    }
                    reverseBytes(strBytes)
                    val chapterTitle = if (j < chapters.size) chapters[j] else "Chapter ${j + 1}"
                    val chapterContent = String(strBytes, Charsets.UTF_16LE).replace("\u2029", "\n")
                    sb.append("## ").append(chapterTitle).append("\n\n").append(chapterContent).append("\n\n")
                }
            }
            return sb.toString().trim()
        }

        private fun getContentText(index: Int): String? {
            return try {
                val rawBytes = getContentBytes(index) ?: return null
                val newBytes = ByteArray(0x8000)
                val inflater = Inflater()
                inflater.setInput(rawBytes)
                val inflatedLen = inflater.inflate(newBytes)
                inflater.end()
                val strBytes = newBytes.copyOf(inflatedLen)
                reverseBytes(strBytes)
                String(strBytes, Charsets.UTF_16LE).replace("\u2029", "\n")
            } catch (e: Exception) {
                null
            }
        }

        private fun getContentBytes(index: Int): ByteArray? {
            FileInputStream(bookPath).use { fis ->
                val dis = DataInputStream(fis)
                val skipLength = contentArr[index].index
                var skipped = 0L
                while (skipped < skipLength) {
                    val sk = dis.skip(skipLength - skipped)
                    if (sk <= 0) break
                    skipped += sk
                }
                val length = contentArr[index].length.toInt()
                val bytes = ByteArray(length)
                dis.readFully(bytes)
                return bytes
            }
        }

        private fun readUInt32(dis: DataInputStream): Long {
            val buf = ByteArray(4)
            dis.readFully(buf)
            currentPoint += 4
            val buffer = ByteBuffer.wrap(buf)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return buffer.int.toLong() and 0xFFFFFFFFL
        }

        private fun readInt32(dis: DataInputStream): Int {
            val buf = ByteArray(4)
            dis.readFully(buf)
            currentPoint += 4
            val buffer = ByteBuffer.wrap(buf)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return buffer.int
        }

        private fun readInt16(dis: DataInputStream): Short {
            val buf = ByteArray(2)
            dis.readFully(buf)
            currentPoint += 2
            val buffer = ByteBuffer.wrap(buf)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return buffer.short
        }

        private fun readByte(dis: DataInputStream): Byte {
            currentPoint += 1
            return dis.readByte()
        }

        private fun readBytes(dis: DataInputStream, num: Int): ByteArray? {
            if (num <= 0) return null
            val buf = ByteArray(num)
            dis.readFully(buf)
            currentPoint += num
            return buf
        }

        private fun readBytes(dis: DataInputStream, bytes: ByteArray): Int {
            return try {
                dis.readFully(bytes)
                currentPoint += bytes.size
                bytes.size
            } catch (e: java.io.EOFException) {
                -1
            }
        }

        private fun readString(dis: DataInputStream, length: Int): String {
            val buf = readBytes(dis, length) ?: return ""
            reverseBytes(buf)
            return String(buf, Charsets.UTF_16LE)
        }

        private fun reverseBytes(bytes: ByteArray) {
            val length = bytes.size
            for (i in 0 until length step 2) {
                if (i + 1 < length) {
                    val temp = bytes[i]
                    bytes[i] = bytes[i + 1]
                    bytes[i + 1] = temp
                }
            }
        }
    }

    private data class Content(val index: Long, val length: Long)
}
