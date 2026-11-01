// The GitHub calls the reward check needs: the merged pull request with the issues it closes and the files it changes,
// recently merged pull requests (for the sweep), and a comment.
const PULL_REQUEST = `query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      number url merged baseRefName body author { login ... on User { databaseId } }
      files(first: 100) { totalCount nodes { path changeType } }
      closingIssuesReferences(first: 10) {
        totalCount
        nodes {
          number title url body state author { login ... on User { databaseId } }
          labels(first: 20) { nodes { name } } assignees(first: 10) { nodes { login } }
          timelineItems(itemTypes: [CLOSED_EVENT], last: 1) { nodes { ... on ClosedEvent { closer { ... on PullRequest { url } } } } }
        }
      }
    }
  }
}`;

export function github({ token, repo, fetch = globalThis.fetch }) {
  const [owner, name] = repo.split('/');
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'field-atlas-rewards', 'Content-Type': 'application/json' };
  const call = async (method, path, body) => {
    const response = await fetch(`https://api.github.com${path}`, { method, headers, body: body && JSON.stringify(body) });
    if (!response.ok) throw new Error(`GitHub answered ${response.status} to ${method} ${path}`);
    return response.json();
  };
  return {
    async pullRequest(number) {
      const answer = await call('POST', '/graphql', { query: PULL_REQUEST, variables: { owner, name, number } });
      if (answer.errors?.length) throw new Error(`GitHub: ${answer.errors.map(e => e.message).join('; ')}`);
      const pr = answer.data.repository.pullRequest;
      return {
        number: pr.number, url: pr.url, merged: pr.merged, baseRef: pr.baseRefName, body: pr.body,
        author: pr.author?.login ?? null, authorId: pr.author?.databaseId ?? null,
        files: (pr.files?.nodes || []).map(file => file.path), filesTotal: pr.files?.totalCount ?? null,
        renamed: (pr.files?.nodes || []).filter(file => file.changeType === 'RENAMED').map(file => file.path),
        issuesTotal: pr.closingIssuesReferences.totalCount ?? null,
        issues: pr.closingIssuesReferences.nodes.map(issue => ({
          number: issue.number, title: issue.title, url: issue.url, body: issue.body,
          author: issue.author?.login ?? null, authorId: issue.author?.databaseId ?? null,
          labels: issue.labels.nodes.map(label => label.name), assignees: issue.assignees.nodes.map(user => user.login),
          state: issue.state ?? null,
          // The latest close only, by URL: a pull request with the same number in another repository is not this one.
          closedBy: (issue.timelineItems?.nodes || []).map(event => event?.closer?.url).filter(url => typeof url === 'string'),
        })),
      };
    },
    /** Numbers of pull requests merged into this repository since a date (YYYY-MM-DD). */
    async mergedSince(date) {
      const q = encodeURIComponent(`repo:${owner}/${name} is:pr is:merged merged:>=${date}`);
      const numbers = [];
      // Search returns at most 1,000 results in pages of 100; more than that is reported rather than cut off quietly.
      for (let page = 1; ; page++) {
        const answer = await call('GET', `/search/issues?q=${q}&per_page=100&page=${page}`);
        const items = answer.items || [];
        numbers.push(...items.map(item => item.number));
        if (items.length < 100) break;
        if (page === 10) throw new Error(`more than 1,000 pull requests were merged since ${date}; the sweep cannot read them all.`);
      }
      return numbers;
    },
    comment(number, body) {
      return call('POST', `/repos/${owner}/${name}/issues/${number}/comments`, { body });
    },
  };
}
