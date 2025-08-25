def parse_args():
    # Format for command line: subject verb flags
    # Options for first positional argument: login, instance, project, label, label version, versioned items, and logout
    parent_parser = argparse.ArgumentParser(
        description="description: sample Python CLI to perform queries using the GraphiQL API.")
    parent_parser.add_argument('--server', type=str, required=True,
                               help="provide link to GraphiQL API for the commands to run")
    # 👇 NEW: Accept --non-interactive so Jenkins doesn't break
    parent_parser.add_argument('--non-interactive', action='store_true',
                               help="Run in CI-safe mode: disable interactive prompts and fail instead.")
