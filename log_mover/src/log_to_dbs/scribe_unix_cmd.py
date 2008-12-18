# (c) Copyright 2008 Cloudera, Inc.
"""
A class to deal with Scribe logs that
came from the I{HadoopMonitoring/Cron}
project
"""

import abstract_scribe

class ScribeUnixCMDLogToDB(abstract_scribe.AbstractScribeLogToDB):
  """
  A LogToDB class that moves any command
  generated by a unix binary and collected
  by Scribe

  It does so by using the last_read table
  to learn what was read when this class was
  last invoked
  """

  # the parser to parse logs
  __parser = None

  def __init__(self, parser, file_prefix):
    """
    Initializes the parser to parse
    the logs along with the file prefix

    @type  parser     : Parser
    @param parser     : the parser that should parse this unix
                        command's output
    @type  file_prefix: string
    @param file_prefix: the Scribe file prefix
    """
    self.__parser = parser
    abstract_scribe.AbstractScribeLogToDB.__init__(self, file_prefix);

  def handle_lines(self, lines):
    """
    Implementation of the abstract method read
    a series of lines that were read from
    scribe.  The size of these lines is
    dependant on the lines themselves along with
    the BUFFER_SIZE constant defined in
    abstract_scribe.AbstractScribeLogToDB

    @type  lines: string list
    @param lines: list of log lines
    """
    for line in lines:
      line = line.strip()
      if line != '':
        # get the stats for this line by parsing
        # the line
        stats = self.__parser.process_line(line)
        # then store the stats
        self.__parser.store_line(stats)