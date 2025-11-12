def p = ["bash","-c","cd /var/lib/jenkins/workspace/MotioCI/api/CLI && python3 ci-cli.py --help"].execute()
def out = new StringBuffer()
p.consumeProcessOutput(out,new StringBuffer())
p.waitFor()
println out
