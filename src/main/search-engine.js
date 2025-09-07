const fs = require('fs');
const readline = require('readline');

class SearchEngine {
  async searchInFile(filePath, query, options = {}) {
    const {
      useRegex = false,
      matchCase = false,
      wholeWord = false,
      useLogcatSyntax = true
    } = options;
    
    const results = [];
    let lineNumber = 1;
    
    // 解析Logcat语法
    const searchPattern = useLogcatSyntax ? 
      this.parseLogcatQuery(query) : 
      this.createSearchPattern(query, useRegex, matchCase, wholeWord);
    
    const readStream = fs.createReadStream(filePath, {
      encoding: 'utf8',
      highWaterMark: 1024 * 1024 // 1MB chunks
    });
    
    const rl = readline.createInterface({
      input: readStream,
      crlfDelay: Infinity
    });
    
    for await (const line of rl) {
      if (this.matchLine(line, searchPattern, options)) {
        results.push({
          line: lineNumber,
          content: line,
          file: filePath
        });
      }
      lineNumber++;
    }
    
    return results;
  }
  
  parseLogcatQuery(query) {
    const patterns = {
      level: /level:([A-Z])/i,
      tag: /tag:(\w+)/i,
      pid: /pid:(\d+)/i,
      tid: /tid:(\d+)/i,
      package: /package:([\w.]+)/i
    };
    
    const regexParts = [];
    let remainingQuery = query;
    
    for (const [key, pattern] of Object.entries(patterns)) {
      const match = remainingQuery.match(pattern);
      if (match) {
        regexParts.push(`(?=.*\\b${key}=[^\\n]*${match[1]}\\b)`);
        remainingQuery = remainingQuery.replace(match[0], '');
      }
    }
    
    remainingQuery = remainingQuery.trim();
    if (remainingQuery) {
      regexParts.push(`(?=.*${this.escapeRegex(remainingQuery)})`);
    }
    
    return regexParts.join('');
  }
  
  createSearchPattern(query, useRegex, matchCase, wholeWord) {
    if (useRegex) {
      return matchCase ? new RegExp(query) : new RegExp(query, 'i');
    }
    
    const escapedQuery = this.escapeRegex(query);
    const pattern = wholeWord ? `\\b${escapedQuery}\\b` : escapedQuery;
    return matchCase ? new RegExp(pattern) : new RegExp(pattern, 'i');
  }
  
  escapeRegex(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
  
  matchLine(line, pattern, options) {
    if (options.useLogcatSyntax) {
      return new RegExp(pattern, 's').test(line);
    }
    return pattern.test(line);
  }
}

module.exports = SearchEngine;