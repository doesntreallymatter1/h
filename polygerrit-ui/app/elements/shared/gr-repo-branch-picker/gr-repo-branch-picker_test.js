/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-repo-branch-picker.js';

const basicFixture = fixtureFromElement('gr-repo-branch-picker');

suite('gr-repo-branch-picker tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('_getRepoSuggestions', () => {
    setup(() => {
      sinon.stub(element.restApiService, 'getRepos')
          .returns(Promise.resolve([
            {
              id: 'plugins%2Favatars-external',
              name: 'plugins/avatars-external',
            }, {
              id: 'plugins%2Favatars-gravatar',
              name: 'plugins/avatars-gravatar',
            }, {
              id: 'plugins%2Favatars%2Fexternal',
              name: 'plugins/avatars/external',
            }, {
              id: 'plugins%2Favatars%2Fgravatar',
              name: 'plugins/avatars/gravatar',
            },
          ]));
    });

    test('converts to suggestion objects', () => {
      const input = 'plugins/avatars';
      return element._getRepoSuggestions(input).then(suggestions => {
        assert.isTrue(element.restApiService.getRepos.calledWith(input));
        const unencodedNames = [
          'plugins/avatars-external',
          'plugins/avatars-gravatar',
          'plugins/avatars/external',
          'plugins/avatars/gravatar',
        ];
        assert.deepEqual(suggestions.map(s => s.name), unencodedNames);
        assert.deepEqual(suggestions.map(s => s.value), unencodedNames);
      });
    });
  });

  suite('_getRepoBranchesSuggestions', () => {
    setup(() => {
      sinon.stub(element.restApiService, 'getRepoBranches')
          .returns(Promise.resolve([
            {ref: 'refs/heads/stable-2.10'},
            {ref: 'refs/heads/stable-2.11'},
            {ref: 'refs/heads/stable-2.12'},
            {ref: 'refs/heads/stable-2.13'},
            {ref: 'refs/heads/stable-2.14'},
            {ref: 'refs/heads/stable-2.15'},
          ]));
    });

    test('converts to suggestion objects', () => {
      const repo = 'gerrit';
      const branchInput = 'stable-2.1';
      element.repo = repo;
      return element._getRepoBranchesSuggestions(branchInput)
          .then(suggestions => {
            assert.isTrue(element.restApiService.getRepoBranches.calledWith(
                branchInput, repo, 15));
            const refNames = [
              'stable-2.10',
              'stable-2.11',
              'stable-2.12',
              'stable-2.13',
              'stable-2.14',
              'stable-2.15',
            ];
            assert.deepEqual(suggestions.map(s => s.name), refNames);
            assert.deepEqual(suggestions.map(s => s.value), refNames);
          });
    });

    test('filters out ref prefix', () => {
      const repo = 'gerrit';
      const branchInput = 'refs/heads/stable-2.1';
      element.repo = repo;
      return element._getRepoBranchesSuggestions(branchInput)
          .then(suggestions => {
            assert.isTrue(element.restApiService.getRepoBranches.calledWith(
                'stable-2.1', repo, 15));
          });
    });

    test('does not query when repo is unset', () => element
        ._getRepoBranchesSuggestions('')
        .then(() => {
          assert.isFalse(element.restApiService.getRepoBranches.called);
          element.repo = 'gerrit';
          return element._getRepoBranchesSuggestions('');
        })
        .then(() => {
          assert.isTrue(element.restApiService.getRepoBranches.called);
        }));
  });
});

