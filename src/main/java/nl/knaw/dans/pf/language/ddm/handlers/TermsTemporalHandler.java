/**
 * Copyright (C) ${project.inceptionYear} DANS - Data Archiving and Networked Services (info@dans.knaw.nl) Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package nl.knaw.dans.pf.language.ddm.handlers;

import nl.knaw.dans.pf.language.ddm.handlermaps.NameSpace;
import nl.knaw.dans.pf.language.ddm.handlertypes.BasicStringHandler;
import nl.knaw.dans.pf.language.emd.types.BasicString;
import nl.knaw.dans.pf.language.emd.types.EmdConstants;
import org.xml.sax.SAXException;

public class TermsTemporalHandler extends BasicStringHandler {

    private final NameSpace namespace;

    public TermsTemporalHandler() {
        this.namespace = null;
    }

    public TermsTemporalHandler(NameSpace namespace) {
        super(null, EmdConstants.SCHEME_ID_TEMPORAL);
        this.namespace = namespace;
    }

    @Override
    protected void finishElement(final String uri, final String localName) throws SAXException {
        BasicString basicString = createBasicString(uri, localName);
        if (basicString != null) {
            if (this.namespace != null)
                basicString.setScheme(this.namespace.prefix.toUpperCase());
            getTarget().getEmdCoverage().getTermsTemporal().add(basicString);
        }
    }
}
