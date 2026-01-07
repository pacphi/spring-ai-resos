#!/usr/bin/env node

/**
 * CSV Validation Script
 * Validates CSV files in the project for structure and basic integrity
 */

import { parse } from 'csv-parse';
import { createReadStream, readdirSync, statSync, readFileSync } from 'fs';
import { join, extname } from 'path';

const SEED_DATA_DIR = './backend/seed-data';

/**
 * Find all CSV files in a directory
 */
function findCsvFiles(dir, files = []) {
  try {
    const items = readdirSync(dir);
    for (const item of items) {
      const fullPath = join(dir, item);
      const stat = statSync(fullPath);
      if (stat.isDirectory()) {
        findCsvFiles(fullPath, files);
      } else if (extname(item).toLowerCase() === '.csv') {
        files.push(fullPath);
      }
    }
  } catch (err) {
    // Directory doesn't exist or not readable
  }
  return files;
}

/**
 * Detect delimiter from first line of file using synchronous read for reliability
 */
function detectDelimiter(filePath) {
  try {
    const content = readFileSync(filePath, 'utf8');
    const newlineIndex = content.indexOf('\n');
    const firstLine = newlineIndex !== -1 ? content.substring(0, newlineIndex) : content;

    // Count occurrences of common delimiters
    const semicolons = (firstLine.match(/;/g) || []).length;
    const commas = (firstLine.match(/,/g) || []).length;
    const tabs = (firstLine.match(/\t/g) || []).length;

    if (semicolons > commas && semicolons > tabs) {
      return ';';
    } else if (tabs > commas && tabs > semicolons) {
      return '\t';
    }
    return ',';
  } catch (err) {
    return ',';
  }
}

/**
 * Validate a single CSV file
 */
async function validateCsv(filePath) {
  const errors = [];
  const warnings = [];
  let rowCount = 0;
  let headers = null;

  const delimiter = detectDelimiter(filePath);

  return new Promise((resolve) => {
    const parser = parse({
      delimiter,
      skip_empty_lines: false,
      relax_column_count: false,
      trim: true,
      relax_quotes: true, // Allow quotes in unquoted fields (for JSON data)
    });

    parser.on('readable', function () {
      let record;
      while ((record = parser.read()) !== null) {
        rowCount++;
        if (rowCount === 1) {
          headers = record;
          // Check for empty headers
          const emptyHeaders = headers.filter((h) => !h || h.trim() === '');
          if (emptyHeaders.length > 0) {
            warnings.push(`Found ${emptyHeaders.length} empty header(s)`);
          }
          // Check for duplicate headers
          const duplicates = headers.filter((h, i) => headers.indexOf(h) !== i);
          if (duplicates.length > 0) {
            errors.push(`Duplicate headers found: ${[...new Set(duplicates)].join(', ')}`);
          }
        } else {
          // Check for row/header column count mismatch
          if (headers && record.length !== headers.length) {
            errors.push(
              `Row ${rowCount}: Expected ${headers.length} columns, found ${record.length}`
            );
          }
        }
      }
    });

    parser.on('error', function (err) {
      errors.push(`Parse error: ${err.message}`);
      resolve({ filePath, valid: false, rowCount, delimiter, errors, warnings });
    });

    parser.on('end', function () {
      resolve({
        filePath,
        valid: errors.length === 0,
        rowCount: rowCount - 1, // Exclude header row
        headers: headers?.length || 0,
        delimiter,
        errors,
        warnings,
      });
    });

    createReadStream(filePath).pipe(parser);
  });
}

/**
 * Main validation function
 */
async function main() {
  console.log('CSV Validation Report');
  console.log('=====================\n');

  // Find CSV files in seed-data directory
  const csvFiles = findCsvFiles(SEED_DATA_DIR);

  if (csvFiles.length === 0) {
    console.log(`No CSV files found in ${SEED_DATA_DIR}`);
    process.exit(0);
  }

  console.log(`Found ${csvFiles.length} CSV file(s)\n`);

  let hasErrors = false;
  const results = [];

  for (const file of csvFiles) {
    const result = await validateCsv(file);
    results.push(result);

    const status = result.valid ? '\u2713' : '\u2717';
    const statusColor = result.valid ? '\x1b[32m' : '\x1b[31m';
    const delimiterName =
      result.delimiter === ';' ? 'semicolon' : result.delimiter === '\t' ? 'tab' : 'comma';
    console.log(`${statusColor}${status}\x1b[0m ${file}`);
    console.log(
      `   Rows: ${result.rowCount}, Columns: ${result.headers}, Delimiter: ${delimiterName}`
    );

    if (result.warnings.length > 0) {
      result.warnings.forEach((w) => console.log(`   \x1b[33mWarning: ${w}\x1b[0m`));
    }

    if (result.errors.length > 0) {
      hasErrors = true;
      result.errors.forEach((e) => console.log(`   \x1b[31mError: ${e}\x1b[0m`));
    }

    console.log('');
  }

  // Summary
  console.log('Summary');
  console.log('-------');
  const validCount = results.filter((r) => r.valid).length;
  console.log(`Valid: ${validCount}/${results.length}`);

  if (hasErrors) {
    console.log('\n\x1b[31mValidation failed with errors\x1b[0m');
    process.exit(1);
  } else {
    console.log('\n\x1b[32mAll CSV files validated successfully\x1b[0m');
    process.exit(0);
  }
}

main();
