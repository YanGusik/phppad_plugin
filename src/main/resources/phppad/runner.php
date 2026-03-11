<?php

// Tinkerwell Lite - PHP Runner
// Receives base64-encoded JSON payload via argv[1]
// Returns JSON result to stdout

$payload = json_decode(base64_decode($argv[1] ?? ''), true);
$code = $payload['code'] ?? '';
$projectPath = $payload['projectPath'] ?? getcwd();

// Calculate line offset before stripping (for magic comment line numbers)
preg_match('/^\s*<\?php\s*/i', $code, $mcPrefix);
$mcLineOffset = isset($mcPrefix[0]) ? substr_count($mcPrefix[0], "\n") : 0;

// Strip opening PHP tag if present
$code = preg_replace('/^\s*<\?php\s*/i', '', $code);

$result = [
    'returnValue' => null,
    'output'      => '',
    'queries'     => [],
    'exception'   => null,
    'duration'    => 0,
];

// Bootstrap Laravel if available
$bootstrapped = false;
$bootstrapError = null;
if (!$projectPath) {
    $bootstrapError = 'Project path is not set';
} elseif (!file_exists($projectPath . '/vendor/autoload.php')) {
    $bootstrapError = 'vendor/autoload.php not found at: ' . $projectPath;
} elseif (!file_exists($projectPath . '/artisan')) {
    $bootstrapError = 'artisan not found at: ' . $projectPath;
}
if (!$bootstrapError) {
    try {
        require_once $projectPath . '/vendor/autoload.php';
        $app = require_once $projectPath . '/bootstrap/app.php';
        $kernel = $app->make(\Illuminate\Contracts\Console\Kernel::class);
        $kernel->bootstrap();
        $bootstrapped = true;

        // Register alias autoloader (like Laravel Tinker)
        // Resolves short class names: User -> App\Models\User
        $classLoader = require $projectPath . '/vendor/composer/autoload_classmap.php';
        $classes = array_keys($classLoader);
        spl_autoload_register(function ($class) use ($classes) {
            if (strpos($class, '\\') !== false) return;
            foreach ($classes as $fqcn) {
                if (basename(str_replace('\\', '/', $fqcn)) === $class) {
                    class_alias($fqcn, $class);
                    return;
                }
            }
        });
    } catch (\Throwable $e) {
        $bootstrapError = get_class($e) . ': ' . $e->getMessage()
            . ' in ' . str_replace($projectPath, '', $e->getFile())
            . ':' . $e->getLine();
        $result['exception'] = [
            'class'   => get_class($e),
            'message' => $e->getMessage(),
            'file'    => str_replace($projectPath, '', $e->getFile()),
            'line'    => $e->getLine(),
        ];
        $result['bootstrapError'] = $bootstrapError;
        echo json_encode($result);
        exit(1);
    }
}

// Inject SQL logging for Laravel
$queries = [];
$queryCode = '';
if ($bootstrapped) {
    $queryCode = '
try {
    \DB::listen(function($query) use (&$__tw_queries) {
        try {
            $grammar = $query->connection->getQueryGrammar();
            $sql = method_exists($grammar, "substituteBindingsIntoRawSql")
                ? $grammar->substituteBindingsIntoRawSql($query->sql, $query->connection->prepareBindings($query->bindings))
                : $query->sql;
            $__tw_queries[] = ["sql" => $sql, "time" => $query->time];
        } catch (\Throwable $e) {}
    });
} catch (\Throwable $e) {}
';
}

// Hoist use declarations to top level (they are invalid inside closures)
$useStatements = [];
$code = preg_replace_callback('/^[ \t]*use\s+[^;{]+;[ \t]*$/m', function ($m) use (&$useStatements) {
    $useStatements[] = trim($m[0]);
    return '';
}, $code);
$useBlock = $useStatements ? implode("\n", $useStatements) . "\n" : '';

// Replace dd() with dump() — in REPL context die() breaks JSON output; value is still captured
$code = preg_replace('/\bdd\s*\(/', 'dump(', $code);

// Transform magic comments (//?  /*?*/  /*?->method()*/  /*?.*/)
$code = transformMagicComments($code, $mcLineOffset);

// Transform code to capture multiple expression values
$code = captureExpressions($code);

// Wrap user code
$wrapped = '<?php
' . $useBlock . '
$__mc_start = microtime(true);
$GLOBALS["__mc"] = [];
$GLOBALS["__mc_start"] = $__mc_start;
$__tw_queries = [];
' . $queryCode . '
$__tw_return = (function() use (&$__mc, $__mc_start) {
' . $code . '
})();
$__mc = $GLOBALS["__mc"];
';

$start = microtime(true);

// Capture output
ob_start();
$returnValue = null;
$exception = null;

try {
    $tmpFile = tempnam(sys_get_temp_dir(), 'tw_exec_');
    file_put_contents($tmpFile, $wrapped);

    // Execute in isolated scope
    $__tw_queries = [];
    $__tw_return = null;
    $__mc = [];

    $closure = function() use ($tmpFile, &$__tw_queries, &$__tw_return, &$__mc) {
        require $tmpFile;
    };
    $closure();

    $returnValues = is_array($__tw_return) ? $__tw_return : [];
    $queries = $__tw_queries ?? [];

} catch (\Throwable $e) {
    $exception = [
        'class'   => get_class($e),
        'message' => $e->getMessage(),
        'file'    => str_replace($projectPath, '', $e->getFile()),
        'line'    => $e->getLine(),
        'trace'   => array_map(function($f) use ($projectPath) {
            return [
                'file' => str_replace($projectPath, '', $f['file'] ?? ''),
                'line' => $f['line'] ?? 0,
                'call' => ($f['class'] ?? '') . ($f['type'] ?? '') . ($f['function'] ?? ''),
            ];
        }, array_slice($e->getTrace(), 0, 10)),
    ];
} finally {
    if (isset($tmpFile) && file_exists($tmpFile)) {
        unlink($tmpFile);
    }
}

$output = ob_get_clean();
$duration = round((microtime(true) - $start) * 1000, 2);

$result = [
    'returnValues'   => array_values(array_map('serializeValue', $returnValues ?? [])),
    'output'         => $output,
    'queries'        => $queries,
    'exception'      => $exception,
    'duration'       => $duration,
    'phpVersion'     => PHP_VERSION,
    'bootstrapped'   => $bootstrapped,
    'bootstrapError' => $bootstrapError ?? null,
    'projectPath'    => $projectPath,
    'magicValues'    => !empty($__mc) ? array_map('serializeValue', $__mc) : null,
];

echo "\n__TWLITE_JSON__\n";
echo json_encode($result, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);

// ─── Magic Comments ─────────────────────────────────────────────────────────

function transformMagicComments(string $code, int $lineOffset): string
{
    // Pass 1: //? at end of line  →  $__mc[LINE] = (expr);
    $lines = explode("\n", $code);
    foreach ($lines as $i => &$line) {
        $lineNum = $i + 1 + $lineOffset;
        if (preg_match('/^([ \t]*)(.*\S)\s*\/\/\?\s*$/', $line, $m)) {
            $stmt = rtrim($m[2], ';');
            $indent = $m[1];

            // If stmt starts with ] or ) it's the closing line of a multi-line expression.
            // Wrapping it inline as a function argument would produce invalid PHP.
            // Instead, scan backward to find the assigned variable and append capture on the same line.
            // IMPORTANT: must stay on the same line — inserting extra lines shifts Pass 2 line numbers.
            if (preg_match('/^[\])]/', ltrim($stmt))) {
                $captureVar = null;
                $depth = 0;
                for ($j = $i; $j >= 0; $j--) {
                    $l = $lines[$j];
                    $depth += substr_count($l, ')') + substr_count($l, ']')
                            - substr_count($l, '(') - substr_count($l, '[');
                    if ($depth <= 0) {
                        if (preg_match('/^\s*(\$\w+)\s*=[^=]/', $l, $vm)) {
                            $captureVar = $vm[1];
                        }
                        break;
                    }
                }
                if ($captureVar !== null) {
                    // Append capture on the SAME line to avoid shifting subsequent line numbers
                    $line = $indent . $m[2] . ' $GLOBALS["__mc"][' . $lineNum . '][] = ' . $captureVar . ';';
                } else {
                    // Fallback: wrap inline (may produce a parse error for truly complex cases)
                    $line = $indent . '(function($__v) { $GLOBALS["__mc"][' . $lineNum . '][] = $__v; return $__v; })(' . $stmt . ');';
                }
            } else {
                $line = $indent . '(function($__v) { $GLOBALS["__mc"][' . $lineNum . '][] = $__v; return $__v; })(' . $stmt . ');';
            }
        }
    }
    unset($line);
    $code = implode("\n", $lines);

    // Pass 2: inline /*?.*/  /*?->method()*/  /*?*/
    $offset = 0;
    $result = '';
    while (preg_match('/\/\*\?((?:[^*]|\*(?!\/))*)\*\//', $code, $match, PREG_OFFSET_CAPTURE, $offset)) {
        $commentPos     = $match[0][1];
        $commentLen     = strlen($match[0][0]);
        $commentContent = trim($match[1][0]);
        $lineNum        = substr_count($code, "\n", 0, $commentPos) + 1 + $lineOffset;

        // Skip /*?*/ that appears inside a // line comment
        $nlPos     = strrpos(substr($code, 0, $commentPos), "\n");
        $lineStart = $nlPos === false ? 0 : $nlPos + 1;
        $linePrefix = substr($code, $lineStart, $commentPos - $lineStart);
        if (preg_match('/\/\//', $linePrefix)) {
            $result .= substr($code, $offset, $commentPos - $offset + $commentLen);
            $offset = $commentPos + $commentLen;
            continue;
        }

        [$expr, $exprStart, $skippedSemi] = findExprBefore($code, $commentPos);

        if ($commentContent === '.') {
            // /*?.*/ – timing. If preceded by an expression, close it first with ;
            $before = substr($code, $offset, $commentPos - $offset);
            $trimmedBefore = rtrim($before);
            $needsSemi = $trimmedBefore !== '' && !in_array(substr($trimmedBefore, -1), [';', '{', '}', '(', ',']);
            // Use direct assignment (not a closure) so captureExpressions does not capture null as a result
            $replacement = ($needsSemi ? '; ' : '') .
                '$GLOBALS["__mc"][' . $lineNum . '][] = round((microtime(true) - $GLOBALS["__mc_start"]) * 1000, 2) . "ms"';
            $result .= $before;
            $result .= $replacement;
        } elseif ($expr !== '') {
            if ($commentContent === '') {
                // /*?*/ – capture value and return it for chaining
                $replacement = '(function($__v) { $GLOBALS["__mc"][' . $lineNum . '][] = $__v; return $__v; })(' . $expr . ')';
            } else {
                // /*?->method()*/ – call method for display, return original value
                $replacement = '(function($__v) { $GLOBALS["__mc"][' . $lineNum . '][] = $__v' . $commentContent . '; return $__v; })(' . $expr . ')';
            }
            $result .= substr($code, $offset, $exprStart - $offset);
            $result .= $replacement;
            // Restore the semicolon we skipped (so statements don't merge)
            if ($skippedSemi) $result .= ';';
        } else {
            // No expression found – remove comment silently
            $result .= substr($code, $offset, $commentPos - $offset);
        }

        $offset = $commentPos + $commentLen;
    }
    $result .= substr($code, $offset);
    return $result;
}

// Scan backwards from $endPos to find the PHP expression immediately before it.
// Returns [exprString, exprStartPos].
function findExprBefore(string $code, int $endPos): array
{
    $i = $endPos - 1;
    while ($i >= 0 && ($code[$i] === ' ' || $code[$i] === "\t")) $i--;
    if ($i < 0) return ['', $endPos];

    // Skip one semicolon so /*?->x()*/ works after a statement ending with ;
    $skippedSemi = false;
    if ($code[$i] === ';') {
        $skippedSemi = true;
        $i--;
        while ($i >= 0 && ($code[$i] === ' ' || $code[$i] === "\t" || $code[$i] === "\n" || $code[$i] === "\r")) $i--;
        if ($i < 0) return ['', $endPos];
    }

    $exprEnd = $i;
    $depth   = 0;
    $hadSemi = $skippedSemi;

    while ($i >= 0) {
        $ch = $code[$i];

        if ($depth > 0) {
            if ($ch === ')' || $ch === ']') $depth++;
            elseif ($ch === '(' || $ch === '[') $depth--;
            $i--;
            continue;
        }

        if ($ch === ')' || $ch === ']') { $depth++; $i--; continue; }

        // ->  and  ?->  operators
        if ($ch === '>' && $i >= 1 && $code[$i - 1] === '-') {
            $i -= ($i >= 2 && $code[$i - 2] === '?') ? 3 : 2;
            while ($i >= 0 && ($code[$i] === ' ' || $code[$i] === "\t" || $code[$i] === "\n" || $code[$i] === "\r")) $i--;
            continue;
        }
        // ::  operator
        if ($ch === ':' && $i >= 1 && $code[$i - 1] === ':') {
            $i -= 2;
            while ($i >= 0 && ($code[$i] === ' ' || $code[$i] === "\t" || $code[$i] === "\n" || $code[$i] === "\r")) $i--;
            continue;
        }

        // Identifier chars / namespace separators / variables
        if (ctype_alnum($ch) || $ch === '_' || $ch === '$' || $ch === '\\') { $i--; continue; }

        break;
    }

    $exprStart = $i + 1;
    $expr = trim(substr($code, $exprStart, $exprEnd - $exprStart + 1));
    return [$expr, $exprStart, $hadSemi];
}

// ─── Helpers ────────────────────────────────────────────────────────────────

// Split PHP code into statements at depth-0 semicolons, respecting strings/braces
function splitStatements(string $code): array
{
    $stmts   = [];
    $current = '';
    $depth   = 0;
    $inStr   = false;
    $strCh   = '';
    $len     = strlen($code);

    for ($i = 0; $i < $len; $i++) {
        $ch = $code[$i];

        // Line comment — consume until newline
        if (!$inStr && $ch === '/' && $i + 1 < $len && $code[$i + 1] === '/') {
            while ($i < $len && $code[$i] !== "\n") $current .= $code[$i++];
            if ($i < $len) $current .= $code[$i];
            continue;
        }

        // Block comment
        if (!$inStr && $ch === '/' && $i + 1 < $len && $code[$i + 1] === '*') {
            $current .= $ch . $code[++$i];
            while ($i + 1 < $len && !($code[$i] === '*' && $code[$i + 1] === '/')) $current .= $code[++$i];
            if ($i + 1 < $len) $current .= $code[++$i];
            continue;
        }

        // String start
        if (!$inStr && ($ch === '"' || $ch === "'")) {
            $inStr = true; $strCh = $ch; $current .= $ch; continue;
        }
        if ($inStr) {
            $current .= $ch;
            if ($ch === $strCh && ($i === 0 || $code[$i - 1] !== '\\')) $inStr = false;
            continue;
        }

        if ($ch === '(' || $ch === '[') { $depth++; $current .= $ch; continue; }
        if ($ch === ')' || $ch === ']') { $depth--; $current .= $ch; continue; }
        if ($ch === '{')                { $depth++; $current .= $ch; continue; }

        if ($ch === '}') {
            $depth--;
            $current .= $ch;
            if ($depth === 0) { $stmts[] = ['code' => $current, 'end' => '}']; $current = ''; }
            continue;
        }

        if ($ch === ';' && $depth === 0) {
            $stmts[] = ['code' => $current, 'end' => ';']; $current = ''; continue;
        }

        $current .= $ch;
    }

    if (trim($current) !== '') $stmts[] = ['code' => $current, 'end' => 'eof'];

    return $stmts;
}

// Transform code so each expression statement captures its value into $__tw_values
function captureExpressions(string $code): string
{
    $stmts = splitStatements($code);
    $out   = [
        '$__tw_values = [];',
        // Intercept dump() so values go into $__tw_values in execution order
        'try {',
        '    \Symfony\Component\VarDumper\VarDumper::setHandler(',
        '        function ($var) use (&$__tw_values) { $__tw_values[] = $var; }',
        '    );',
        '} catch (\Throwable $__e) {}',
    ];

    foreach ($stmts as $stmt) {
        $raw     = $stmt['code'];
        $trimmed = trim($raw);
        $end     = $stmt['end'];

        if ($trimmed === '') continue;

        // Skip statements that consist only of comments (no real code)
        $codeOnly = trim(preg_replace(['/\/\/[^\n]*/m', '/\/\*.*?\*\//s'], '', $trimmed));
        if ($codeOnly === '') continue;

        // Block-level statements (if/while/function bodies ending with })
        if ($end === '}') { $out[] = $trimmed; continue; }

        // After the '}' check above, $end is always ';' or 'eof'.
        // In both cases the statement has no trailing ';' in $trimmed, so we always append one.

        // Control structures / declarations
        if (preg_match('/^(if\s*\(|else[\s{]|elseif\s*\(|while\s*\(|for\s*\(|foreach\s*\(|switch\s*\(|try[\s{]|catch\s*\(|finally[\s{]|class\s|abstract\s|interface\s|trait\s|function\s|namespace\s)/', $trimmed)) {
            $out[] = $trimmed . ';'; continue;
        }

        // Void statements
        if (preg_match('/^(echo[\s(]|print[\s(]|throw\s|unset\s*\(|include\s|require\s|include_once\s|require_once\s)/', $trimmed)) {
            $out[] = $trimmed . ';'; continue;
        }

        // dump/dd — value is captured by the VarDumper handler below, not as expression
        if (preg_match('/^(dump|dd|var_dump|var_export)\s*\(/', $trimmed)) {
            $out[] = $trimmed . ';'; continue;
        }


        // Plain assignment: $var = / $var->prop = / etc. — execute but don't capture
        if (preg_match('/^(\$[\w\[\]\'"\->]+|[\w\\\\]+::\$\w+)\s*(?:\?\?=|\.=|\+=|-=|\*=|\/=|%=|=(?!=))/', $trimmed)) {
            $out[] = $trimmed . ';'; continue;
        }

        // Expression — just execute, no auto-capture (use dump() explicitly)
        $out[] = $trimmed . ';';
    }

    $out[] = 'return $__tw_values;';
    return implode("\n", $out);
}

function serializeValue($value, $depth = 0)
{
    if ($depth > 5) return '...';

    if ($value === null) return ['type' => 'null', 'value' => null];
    if (is_bool($value)) return ['type' => 'bool', 'value' => $value];
    if (is_int($value)) return ['type' => 'int', 'value' => $value];
    if (is_float($value)) return ['type' => 'float', 'value' => $value];
    if (is_string($value)) return ['type' => 'string', 'value' => $value, 'length' => strlen($value)];

    if (is_array($value)) {
        return [
            'type'  => 'array',
            'count' => count($value),
            'items' => array_values(array_map(fn($v) => serializeValue($v, $depth + 1), $value)),
            'keys'  => array_values(array_map('strval', array_keys($value))),
        ];
    }

    if (is_object($value)) {
        $class = get_class($value);
        $props = [];
        $hidden = [];

        // Eloquent model
        if (method_exists($value, 'toArray') && method_exists($value, 'getHidden')) {
            try {
                $hidden = $value->getHidden();
                // visible props
                $props = $value->toArray();
                // also include hidden props for toggle
                $hiddenProps = [];
                foreach ($hidden as $h) {
                    $hiddenProps[$h] = $value->getAttribute($h);
                }
            } catch (\Throwable $e) {
                $props = [];
            }
        } elseif (method_exists($value, 'toArray')) {
            try { $props = $value->toArray(); } catch (\Throwable $e) {}
        } else {
            try { $props = (array) $value; } catch (\Throwable $e) {}
        }

        return [
            'type'   => 'object',
            'class'  => $class,
            'items'  => array_values(array_map(fn($v) => serializeValue($v, $depth + 1), $props)),
            'keys'   => array_values(array_map('strval', array_keys($props))),
            'hidden' => array_values(array_map(fn($v) => serializeValue($v, $depth + 1), $hiddenProps ?? [])),
            'hiddenKeys' => array_values($hidden),
        ];
    }

    return ['type' => 'unknown', 'value' => (string) $value];
}
