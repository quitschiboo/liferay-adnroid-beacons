#!/usr/bin/env python
#
# Copyright 2014 Liferay, Inc. All rights reserved.
# http://www.liferay.com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# This is the module install hook that will be
# called when your module is first installed
#
import os, sys

def main(args,argc):

  # TODO: write your install hook here (optional)

  # exit
  sys.exit(0)



if __name__ == '__main__':
  main(sys.argv,len(sys.argv))
