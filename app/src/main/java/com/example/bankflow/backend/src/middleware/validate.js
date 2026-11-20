'use strict';

const { Errors, IssueCode } = require('../errors');

function entry(code, params) {
  return params ? { code, params } : { code };
}


function toIssue(issue) {
  switch (issue.code) {
    case 'invalid_type':
      if (issue.received === 'undefined') return entry(IssueCode.REQUIRED);
      if (issue.expected === 'integer') return entry(IssueCode.NOT_INTEGER);
      return entry(IssueCode.INVALID_TYPE);

    case 'too_small':
      if (issue.type === 'string') {

        return Number(issue.minimum) <= 1
          ? entry(IssueCode.REQUIRED)
          : entry(IssueCode.TOO_SHORT, { min: Number(issue.minimum) });
      }
      return entry(IssueCode.TOO_SMALL, { min: Number(issue.minimum) });

    case 'too_big':
      return issue.type === 'string'
        ? entry(IssueCode.TOO_LONG, { max: Number(issue.maximum) })
        : entry(IssueCode.TOO_LARGE, { max: Number(issue.maximum) });

    case 'invalid_string':
      if (issue.validation === 'email') return entry(IssueCode.INVALID_EMAIL);
      if (issue.validation === 'uuid') return entry(IssueCode.INVALID_UUID);
      if (issue.validation === 'datetime') return entry(IssueCode.INVALID_DATETIME);
      return entry(IssueCode.INVALID_FORMAT);

    case 'invalid_enum_value':
      return entry(IssueCode.INVALID_OPTION);

    case 'custom': {
      const { code, ...rest } = issue.params ?? {};
      return entry(code ?? IssueCode.INVALID, Object.keys(rest).length > 0 ? rest : undefined);
    }

    default:
      return entry(IssueCode.INVALID);
  }
}

function describe(issues) {
  const fields = {};
  const coded = {};
  const form = [];
  const formIssues = [];

  for (const issue of issues) {
    const field = issue.path.length > 0 ? String(issue.path[0]) : null;
    const item = toIssue(issue);
    if (field === null) {
      form.push(issue.message);
      formIssues.push(item);
    } else {
      (fields[field] ??= []).push(issue.message);
      (coded[field] ??= []).push(item);
    }
  }

  return { fields, issues: coded, ...(form.length > 0 && { form, formIssues }) };
}

function validate(schema, source = 'body') {
  return function validateRequest(req, _res, next) {
    const result = schema.safeParse(req[source]);

    if (!result.success) {
      return next(Errors.validation('Some fields are invalid', describe(result.error.issues)));
    }

    req[source] = result.data;
    next();
  };
}

module.exports = { validate, toIssue };