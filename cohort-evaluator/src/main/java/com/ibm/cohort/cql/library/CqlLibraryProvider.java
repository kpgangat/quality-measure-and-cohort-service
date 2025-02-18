/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.cql.library;

import java.util.Collection;

public interface CqlLibraryProvider {
    public Collection<CqlLibraryDescriptor> listLibraries();

    public CqlLibrary getLibrary(CqlLibraryDescriptor cqlResourceDescriptor);
}
