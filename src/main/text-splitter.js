const fs = require('fs');
const path = require('path');
const readline = require('readline');

class TextSplitter {
    async splitFile(filePath, outputDir) {
        const stats = fs.statSync(filePath);
        const fileSize = stats.size;
        const fileName = path.basename(filePath, '.txt');
        
        // 确定分割数量
        let splitCount;
        if (fileSize > 1000 * 1024 * 1024) { // >1000MB
            splitCount = 100;
        } else if (fileSize > 100 * 1024 * 1024) { // >100MB
            splitCount = Math.floor(fileSize / (10 * 1024 * 1024));
        } else {
            splitCount = 10;
        }
        
        // 确保分割数量在10-100之间
        splitCount = Math.max(10, Math.min(100, splitCount));
        const chunkSize = Math.ceil(fileSize / splitCount);
        
        const chunks = [];
        let currentChunk = 1;
        let currentSize = 0;
        let remaining = '';
        
        const outputFile = path.join(outputDir, `${fileName}_sub${currentChunk}.txt`);
        let writeStream = fs.createWriteStream(outputFile);
        chunks.push(outputFile);
        
        const readStream = fs.createReadStream(filePath, {
            encoding: 'utf8',
            highWaterMark: 1024 * 1024 // 1MB chunks
        });
        
        const rl = readline.createInterface({
            input: readStream,
            crlfDelay: Infinity
        });
        
        for await (const line of rl) {
            const lineContent = remaining + line + '\n';
            remaining = '';
            
            if (currentSize >= chunkSize && currentChunk < splitCount) {
                writeStream.end();
                currentChunk++;
                const newOutputFile = path.join(outputDir, `${fileName}_sub${currentChunk}.txt`);
                writeStream = fs.createWriteStream(newOutputFile);
                chunks.push(newOutputFile);
                currentSize = 0;
            }
            
            writeStream.write(lineContent);
            currentSize += Buffer.byteLength(lineContent, 'utf8');
        }
        
        if (remaining) {
            writeStream.write(remaining);
        }
        
        writeStream.end();
        return chunks;
    }
}

module.exports = TextSplitter;